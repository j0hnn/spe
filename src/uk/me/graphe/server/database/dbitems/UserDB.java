package uk.me.graphe.server.database.dbitems;

import java.util.List;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Id;

@Entity
public class UserDB {

	@Id
	private String mUserId;
	private String mEmail;
	private List<String> mKeys;
	
	// Void constructor for morphia
	public UserDB() {}

	
	public String getEmail() {
		return mEmail;
	}
	
	public void setEmail(String email) {
		mEmail = email;
	}
	
	public UserDB(String id, String e) {
		mUserId = id;
		mEmail = e;
	}
	
	public String getId() {
		return mUserId;
	}
	
	public void addKey(String toAdd) {
		mKeys.add(toAdd);
	}
	
	public void removeKey(String toRemove) {
		mKeys.remove(toRemove);
	}
	
	public List<String> getKeys() {
		return mKeys;
	}
}