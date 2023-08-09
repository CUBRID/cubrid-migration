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
package com.cubrid.cubridmigration.core.engine.report;

import java.io.Serializable;

/**
 * ObjNameMigration
 * 
 * @author CUBRID
 *
 */
public class ObjNameMigrationResult implements 
		Serializable {
	
	private static final long serialVersionUID = -7152528868537532963L;
	private String objType;
	private String objSourceName;
	private String objTargetName;
	
	public ObjNameMigrationResult() { }
	
	public ObjNameMigrationResult(String objType, String objSourceName, String objTargetName) {
		this.objType = objType;
		this.objSourceName = objSourceName;
		this.objTargetName = objTargetName;
	}
	
	public String getObjType() {
		return objType;
	}
	
	public void setObjType(String objType) {
		this.objType = objType;
	}
	
	public String getObjSourceName() {
		return objSourceName;
	}
	
	public void setObjSourceName(String objSourceName) {
		this.objSourceName = objSourceName;
	}
	
	public String getObjTargetName() {
		return objTargetName;
	}
	
	public void setObjTargetName(String objTargetName) {
		this.objTargetName = objTargetName;
	}
	
	public static long getSerialversionuid() {
		return serialVersionUID;
	}
}
