/*
 * Copyright 2016 CUBRID Corporation. All rights reserved by Search Solution. 
 *
 * Redistribution and use in source and binary forms, with or without modification, 
 * are permitted provided that the following conditions are met: 
 *
 * - Redistributions of source code must retain the above copyright notice, 
 *   this list of conditions and the following disclaimer. 
 *
 * - Redistributions in binary form must reproduce the above copyright notice, 
 *   this list of conditions and the following disclaimer in the documentation 
 *   and/or other materials provided with the distribution. 
 *
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors 
 *   may be used to endorse or promote products derived from this software without 
 *   specific prior written permission. 
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
 * OF SUCH DAMAGE. 
 *
 */
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
