package com.cubrid.cubridmigration.core.engine.config;

public class SourceGrantConfig extends 
		SourceConfig {
	private String owner;
	private String targetOwner;
	private String grantorName;
	private String sourceGrantorName;
	private String granteeName;
	private String authType;
	private String className;
	private String classOwner;
	private boolean isGrantable;
	
	public String getOwner() {
		return owner;
	}
	
	public void setOwner(String owner) {
		this.owner = owner;
	}
	
	public String getTargetOwner() {
		return targetOwner;
	}
	
	public void setTargetOwner(String targetOwner) {
		this.targetOwner = targetOwner;
	}
	
	public String getGrantorName() {
		return grantorName;
	}
	
	public void setGrantorName(String grantorName) {
		this.grantorName = grantorName;
	}
	
	public String getSourceGrantorName() {
		return sourceGrantorName;
	}
	
	public void setSourceGrantorName(String sourceGrantorName) {
		this.sourceGrantorName = sourceGrantorName;
	}
	
	public String getGranteeName() {
		return granteeName;
	}
	
	public void setGranteeName(String granteeName) {
		this.granteeName = granteeName;
	}

	public String getAuthType() {
		return authType;
	}
	
	public void setAuthType(String authType) {
		this.authType = authType;
	}
	
	public String getClassName() {
		return className;
	}
	
	public void setClassName(String className) {
		this.className = className;
	}
	
	public String getClassOwner() {
		return classOwner;
	}
	
	public void setClassOwner(String classOwner) {
		this.classOwner = classOwner;
	}
	
	public boolean isGrantable() {
		return isGrantable;
	}
	
	public void setGrantable(boolean isGrantable) {
		this.isGrantable = isGrantable;
	}
}
