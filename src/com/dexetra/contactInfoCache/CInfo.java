package com.dexetra.contactInfoCache;
public class CInfo {
	public long photo_id;
	public String photo_URI;
	public final long contact_id;
	public String lookup_key;
	public String name;
	public boolean  isStarred;
	public int label_type;
	public String label;
	public long lastContactedTsp;
	public String number;

	protected CInfo(long photo_id, String photo_URI, long contact_id,
			String lookup_key, String name,
			boolean isStarred, int label_type, String label,
			long lastContactedTsp, String number) {
		this.photo_id = photo_id;
		this.photo_URI = photo_URI;
		this.contact_id = contact_id;
		this.lookup_key = lookup_key;
		this.name = name;
		this.label_type = label_type;
		this.label = label;
		this.lastContactedTsp = lastContactedTsp;
		this.number = number;
		this.isStarred = isStarred;
	}

	@Override
	public boolean equals(Object o) {
		if (this.contact_id == ((CInfo) o).contact_id) {
			this.name = ((CInfo) o).name;
			this.photo_id = ((CInfo) o).photo_id;
			this.photo_URI = ((CInfo) o).photo_URI;
			this.label = ((CInfo) o).label;
			this.lookup_key = ((CInfo) o).lookup_key;
			this.label_type = ((CInfo) o).label_type;
			this.lastContactedTsp = ((CInfo) o).lastContactedTsp;
			this.number = ((CInfo) o).number;
			this.isStarred = ((CInfo) o).isStarred;
			return true;
		} else
			return false;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append("super : " + super.toString());
		str.append("\n");
		str.append("name : " + name);
		str.append("\n");
		str.append("contact_id : " + contact_id);
		str.append("\n");
		str.append("photo_id : " + photo_id);
		str.append("\n");
		str.append("photo_URI : " + photo_URI);
		str.append("\n");
		str.append("isStarred : " + isStarred);
		str.append("\n");
		str.append("label : " + label);
		str.append("\n");
		str.append("label_type : " + label_type);
		str.append("\n");
		str.append("lastContactedTsp : " + lastContactedTsp);
		return str.toString();
	}
}