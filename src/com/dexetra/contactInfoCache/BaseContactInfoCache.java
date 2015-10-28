package com.dexetra.contactInfoCache;

/**
 * @author Kiran BH 
 *
 */

import android.app.Application;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseContactInfoCache<T extends CInfo> {
    public final boolean DEBUG;
    private static final String UNK = "-1";
    private static String[] PROJECTION = new String[]{Phone.PHOTO_ID,
            Phone.PHOTO_URI, Phone.CONTACT_ID, Phone.NUMBER,
            Phone.DISPLAY_NAME, Phone.TYPE, Phone.LABEL, Phone.STARRED,
            Phone.LAST_TIME_CONTACTED, Phone.LOOKUP_KEY};
    private static String[] PROJECTION_LOOKUP = new String[]{
            PhoneLookup.PHOTO_ID, PhoneLookup.PHOTO_URI, PhoneLookup._ID,
            PhoneLookup.NUMBER, PhoneLookup.DISPLAY_NAME, PhoneLookup.TYPE,
            PhoneLookup.LABEL, PhoneLookup.STARRED,
            PhoneLookup.LAST_TIME_CONTACTED, PhoneLookup.LOOKUP_KEY};
    private static int INDEX_PHOTO_ID = 0;
    private static int INDEX_PHOTO_URI = 1;
    private static int INDEX_CONTACT_ID = 2;
    private static int INDEX_NUMBER = 3;
    private static int INDEX_DISPLAY_NAME = 4;
    private static int INDEX_LOOKUP_KEY = 9;
    private static int INDEX_TYPE = 5;
    private static int INDEX_LABEL = 6;
    private static int INDEX_STARRED = 7;
    private static int INDEX_LAST_TIME_CONTACTED = 8;

    protected Context mContext;
    private PreLoader mPreload;
    private int mActiveCount;
    protected volatile Boolean mValid = false;
    private boolean mIsActive = false;
    private long mLastUpdatedTime = -1;
    protected final Handler mUiThreadHandler;
    public static final String TAG = "CONTACTINFOCACHE";
    private long mLastRequest, mLastCancelled, mLastExecuted;
    protected Map<Long, T> mCache = new HashMap<Long, T>();
    private List<ContactCacheListener> mCacheListeners = new ArrayList<ContactCacheListener>(
            2);
    private List<Long> mStarred = new ArrayList<Long>(4);
    private List<CICContentObesrver> mContentObservers = new ArrayList<CICContentObesrver>(
            5);
    private List<ContactExtraData> mExtraDatas;

    public interface ContactCacheListener {
        public void onCacheChanged();
    }

    protected abstract List<CICContentObesrver> getObservers();

    protected abstract void onStart();

    protected abstract void onStop();

    protected abstract void addData(long num_id, T info);

    protected abstract void onChangeUriContent(Uri uri);

    protected abstract long getNumId(String number);

    protected BaseContactInfoCache(Context context, Handler handler,
                                   boolean debug) {
        if (!(context instanceof Application))
            throw new IllegalArgumentException(
                    "context should be of application class");
        DEBUG = debug;
        mUiThreadHandler = (handler == null) ? new Handler() : handler;
        mContext = context;
        mContentObservers.add(new CICContentObesrver(Phone.CONTENT_URI));
        Collection<CICContentObesrver> obss = getObservers();
        if (obss != null)
            mContentObservers.addAll(obss);

    }

    private void startListening() {
        for (CICContentObesrver obs : mContentObservers)
            mContext.getContentResolver().registerContentObserver(obs.mUri,
                    true, obs);
        mPreload = new PreLoader();
        mPreload.start();
        onStart();
        mIsActive = true;
        if (DEBUG)
            Log.i(TAG, "startListening");
    }

    private void stopListening() {
        for (ContentObserver obs : mContentObservers)
            try {
                mContext.getContentResolver().unregisterContentObserver(obs);
            } catch (Exception e) {
            }
        onStop();
        mIsActive = false;
        if (DEBUG)
            Log.i(TAG, "stopListening");
    }

    public synchronized void start() {
        mActiveCount++;
        startEngine(true);

    }

    public synchronized void stop() {
        mActiveCount--;
        startEngine(false);
    }

    private void startEngine(boolean start) {
        mUiThreadHandler.removeCallbacks(mEngineStarter);
        mUiThreadHandler.postDelayed(mEngineStarter, start ? 0 : 2000);
    }

    Runnable mEngineStarter = new Runnable() {

        @Override
        public void run() {
            if (mActiveCount == 1 && !mIsActive)
                startListening();
            else if (mActiveCount == 0 && mIsActive)
                stopListening();

        }
    };

    public boolean isActive() {
        return mIsActive;
    }

    public void registerCacheListeners(ContactCacheListener cacheChangeNotfier) {
        if (!mCacheListeners.contains(cacheChangeNotfier))
            mCacheListeners.add(cacheChangeNotfier);

    }

    public void unRegisterCacheListeners(ContactCacheListener cacheChangeNotfier) {
        mCacheListeners.remove(cacheChangeNotfier);
    }

    private void noifyCachelisteners() {
        for (int i = 0; i < mCacheListeners.size(); i++) {
            mCacheListeners.get(i).onCacheChanged();
        }
    }

    public boolean isValid() {
        return mValid && mIsActive;
    }

    protected void invalidate() {
        final long reqTsp = System.currentTimeMillis();
        mLastRequest = reqTsp;
        if ((reqTsp - mLastUpdatedTime) > 3000 && mValid && mIsActive) {
            mLastExecuted = reqTsp;
            if (DEBUG)
                Log.w(TAG, "invaldiate  " + mValid);
        } else {
            mLastCancelled = reqTsp;
            if (DEBUG)
                Log.i(TAG, "invaldiate cancelled " + mValid + " t-"
                        + (reqTsp - mLastUpdatedTime));
            if (mIsActive)
                mUiThreadHandler.postDelayed(mChecker, 1000);
            return;
        }
        if (mPreload.isAlive()) {
            mPreload.interrupt();
            if (DEBUG)
                Log.d(TAG, "invaldiate thread interupted ");
        }
        mPreload = new PreLoader();
        mPreload.start();
    }

    private Runnable mChecker = new Runnable() {

        @Override
        public void run() {
            if (mLastExecuted != mLastRequest)
                invalidate();

        }
    };

    public void updateCache(List<ContactExtraData> datas) {
        mExtraDatas = datas;
        if (mValid && datas != null && datas.size() > 0) {
            new Thread(new Runnable() {

                @Override
                public void run() {
                    updateExtraData(mValid, mCache);
                    mUiThreadHandler.post(mAfterUpdatingCache);
                }
            }).start();
        }
    }

    private synchronized void updateExtraData(boolean flag, Map<Long, T> cache) {
        try {
            if (flag && mExtraDatas != null && mExtraDatas.size() > 0) {
                if (DEBUG)
                    Log.i(TAG, "UPDATING EXRADATA  size:" + mExtraDatas.size());
                for (ContactExtraData data : mExtraDatas) {
                    long key = getNumId(data.number);

                    if (data.lastContactedTsp > 0 && cache.containsKey(key)) {
                        T info = cache.get(key);
                        info.lastContactedTsp = data.lastContactedTsp;
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    @SuppressWarnings("unchecked")
    protected T getInfoObject(long photo_id, String photo_URI, long contact_id,
                              String lookup_key, String name, boolean isStarred, int label_type,
                              String label, long lastContactedTsp, String number) {
        return (T) new CInfo(photo_id, photo_URI, contact_id, lookup_key, name,
                isStarred, label_type, label, lastContactedTsp, number);
    }

    private class PreLoader extends Thread {
        public PreLoader() {
            mLastUpdatedTime = System.currentTimeMillis();
        }

        @Override
        public void run() {
            if (DEBUG)
                Log.i(TAG, "RUNNING THE CONTACT CACHE THREAD");
            long DbugTime = System.currentTimeMillis();
            Cursor phone = null;
            try {
                phone = mContext.getContentResolver().query(Phone.CONTENT_URI,
                        PROJECTION, null, null, null);
            } catch (Exception e) {
            }
            Map<Long, T> cache = new HashMap<Long, T>();
            List<Long> starred = new ArrayList<Long>(4);
            if (phone != null && phone.moveToFirst()) {
                do {
                    long numId = getNumId(phone.getString(INDEX_NUMBER));
                    String photoURI = phone.getString(INDEX_PHOTO_URI);
                    if (numId != -1) {
                        long lctsp = phone.getLong(INDEX_LAST_TIME_CONTACTED);
                        if (cache.containsKey(numId)) {
                            T info = cache.get(numId);
                            if (info != null) {
                                if (lctsp > info.lastContactedTsp)
                                    info.lastContactedTsp = lctsp;
                                if (info.photo_URI == null)
                                    info.photo_URI = photoURI;
                            }

                        } else {
                            long contact_id = phone.getLong(INDEX_CONTACT_ID);
                            boolean isStarred = phone.getInt(INDEX_STARRED) == 1;
                            if (isStarred && contact_id != -1
                                    && !mStarred.contains(contact_id))
                                starred.add(contact_id);
                            T info = getInfoObject(
                                    phone.getLong(INDEX_PHOTO_ID), photoURI,
                                    contact_id,
                                    phone.getString(INDEX_LOOKUP_KEY),
                                    phone.getString(INDEX_DISPLAY_NAME),
                                    isStarred, phone.getInt(INDEX_TYPE),
                                    phone.getString(INDEX_LABEL), lctsp,
                                    phone.getString(INDEX_NUMBER));
                            addData(numId, info);
                            if (lctsp > info.lastContactedTsp)
                                info.lastContactedTsp = lctsp;
                            if (info.photo_URI == null)
                                info.photo_URI = photoURI;
                            cache.put(numId, info);
                        }
                    }

                } while (phone.moveToNext());
            }

            if (phone != null)
                phone.close();
            updateExtraData(true, cache);

            cache.remove(-1);
            mValid = false;
            mCache.clear();
            mStarred.clear();
            mCache.putAll(cache);
            mStarred.addAll(starred);
            cache.clear();
            starred.clear();
            cache = null;
            starred = null;
            mValid = true;

            mUiThreadHandler.post(mAfterUpdatingCache);
            super.run();
            if (DEBUG) {
                Log.i(TAG, "FINISHED RUNNING THE CONTACT CACHE THREAD");
                Log.w(TAG, "(rc)ms- " + (System.currentTimeMillis() - DbugTime));
            }
        }

    }

    protected Runnable mAfterUpdatingCache = new Runnable() {

        @Override
        public void run() {
            if (DEBUG)
                Log.i(TAG, "notifying contact listeners");
            noifyCachelisteners();
        }
    };

    public class CICContentObesrver extends ContentObserver {
        Uri mUri;

        public CICContentObesrver(Uri uri) {
            super(mUiThreadHandler);
            mUri = uri;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if (DEBUG)
                Log.d(TAG, "onChange " + mUri + "   " + selfChange);
            if (mUri.equals(Phone.CONTENT_URI)) {
                invalidate();
            } else {
                onChangeUriContent(mUri);
            }
        }
    }

    // User available methods

    protected T getBasicInfo(String number) {
        if (DEBUG)
            Log.d(TAG, "querying basic info of " + number);
        if (number == null || number.length() < 1 || number.equals(UNK))
            return null;
        Uri contactUri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI,
                Uri.encode(number));
        Cursor c = mContext.getContentResolver().query(contactUri, PROJECTION,
                null, null, null);
        T info = null;
        if (c != null && c.moveToFirst())
            info = extractFromCursor(c);
        if (c != null)
            c.close();
        try {
            if (info == null) {
                contactUri = Uri.withAppendedPath(
                        PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
                c = mContext.getContentResolver().query(contactUri,
                        PROJECTION_LOOKUP, null, null, null);
                if (c != null && c.moveToFirst())
                    info = extractFromCursor(c);
                if (c != null)
                    c.close();
            }
        } catch (Exception e) {
        }
        if (info != null) return info;
        long num_id = getNumId(number);
        if (info == null && num_id != -1 && num_id > 9999) {
            c = mContext.getContentResolver().query(Phone.CONTENT_URI,
                    PROJECTION,
                    Phone.NUMBER + " LIKE '%" + num_id + "'", null,
                    Phone.LAST_TIME_CONTACTED + " desc");
            if (c != null && c.moveToNext())
                info = extractFromCursor(c);
            if (c != null)
                c.close();
        }
        if (info == null && num_id != -1) {
            try {
                c = mContext.getContentResolver().query(Phone.CONTENT_URI,
                        PROJECTION, null, null, null);
                if (c != null && c.moveToFirst())
                    do {
                        long t = getNumId(c.getString(INDEX_NUMBER));
                        if (t == num_id) {
                            info = extractFromCursor(c);
                            break;
                        }
                    } while (c.moveToNext());

                if (c != null)
                    c.close();
            } catch (Exception e) {
            }
        }
        if (c != null)
            c.close();
        return info;
    }

    private T extractFromCursor(Cursor c) {
        long contact_id = c.getLong(INDEX_CONTACT_ID);
        String photoURI = c.getString(INDEX_PHOTO_URI);
        return getInfoObject(c.getLong(INDEX_PHOTO_ID), photoURI, contact_id,
                c.getString(INDEX_LOOKUP_KEY), c.getString(INDEX_DISPLAY_NAME),
                c.getInt(INDEX_STARRED) == 1, c.getInt(INDEX_TYPE),
                c.getString(INDEX_LABEL), c.getLong(INDEX_LAST_TIME_CONTACTED),
                c.getString(INDEX_NUMBER));
    }

    public boolean isKnown(String number) {
        long key = getNumId(number);
        if (mValid) {
            if (mCache.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    public String getName(String number) {
        long key = getNumId(number);
        String name = null;
        if (mValid && key != -1) {
            if (mCache.containsKey(key)) {
                name = mCache.get(key).name;
            }
        }
        return name;
    }

    public long getContactId(String number, long def) {
        long key = getNumId(number);
        long contact_id = def;
        if (mValid) {
            if (mCache.containsKey(key)) {
                contact_id = mCache.get(key).contact_id;
            }
        }
        return contact_id;
    }

    public long getPhotoId(long contact_id) {
        long photo_id = 0;
        if (mValid) {
            T test = getInfoObject(-1, "", contact_id, null, null, false, -1,
                    "", -1, null);
            if (mCache.containsValue(test)) {
                photo_id = test.photo_id;
            }
        }
        return photo_id;

    }

    public long getPhotoId(String number) {
        long key = getNumId(number);
        long photo_id = 0;
        if (mValid) {
            if (mCache.containsKey(key)) {
                photo_id = mCache.get(key).photo_id;
            }
        }
        return photo_id;
    }

    public boolean isStarred(String number) {
        long key = getNumId(number);
        boolean starred = false;
        if (mValid) {
            if (mCache.containsKey(key)) {
                starred = mCache.get(key).isStarred;
            }
        }
        return starred;
    }

    public boolean isStarred(long contact_id) {
        return mStarred.contains(contact_id);
    }

    public T getContactInfo(long contact_id) {
        if (mValid) {
            T test = getInfoObject(-1, "", contact_id, null, null, false, -1,
                    "", -1, null);
            if (mCache.containsValue(test)) {
                return test;
            }
        }
        return null;

    }

    public T getContactInfo(String number) {
        return getContactInfo(number, false);
    }

    public T getContactInfo(String number, boolean force) {
        long key = getNumId(number);
        if (mValid && mCache.containsKey(key)) {
            return mCache.get(key);
        } else {
            if (force) {
                return getBasicInfo(number);
            } else
                return null;
        }
    }

    //

    protected void finalize() throws Throwable {
        mCache.clear();
        mCacheListeners.clear();
        mStarred.clear();
        mCache = null;
        mCacheListeners = null;
        mStarred = null;
        super.finalize();
    }
}