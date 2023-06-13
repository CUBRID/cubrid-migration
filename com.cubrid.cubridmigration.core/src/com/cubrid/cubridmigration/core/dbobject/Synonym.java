/*
 * Copyright (C) 2009 Search Solution Corporation. All rights reserved by Search Solution. 
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
package com.cubrid.cubridmigration.core.dbobject;

/**
 * synonym object
 * 
 * @author dongmin kim
 * @version 1.0 - 2023-05-26
 */
public class Synonym extends 
		DBObject implements 
		Cloneable {

	private static final long serialVersionUID = -7332583909384188696L;
	private String name;
	private String owner;
	private boolean isPublic;
	private String objectName;
	private String objectOwner;
	private String comment;
	private String createDDL;
	
	public Synonym() {
		//do nothing
	}
	
	public Synonym(String name, String owner, boolean isPublic,
			String objectName, String objectOwner, String comment,
			String createDDL) {
		this.name = name;
		this.owner = owner;
		this.isPublic = isPublic;
		this.objectName = objectName;
		this.objectOwner = objectOwner;
		this.comment = comment;
		this.createDDL = createDDL;
	}

	/**
	 * clone
	 * 
	 * @return Synonym
	 */
	public Object clone() {
		final Synonym synonym = new Synonym(name, owner,
				isPublic, objectName, objectOwner, comment, 
				createDDL);
		synonym.setName(name);
		synonym.setOwner(owner);
		synonym.setPublic(isPublic);
		synonym.setObjectName(objectName);
		synonym.setObjectOwner(objectOwner);
		synonym.setComment(comment);
		synonym.setDDL(createDDL);
		return synonym;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public boolean isPublic() {
		return isPublic;
	}

	public void setPublic(boolean isPublic) {
		this.isPublic = isPublic;
	}

	public String getObjectName() {
		return objectName;
	}

	public void setObjectName(String objectName) {
		this.objectName = objectName;
	}

	public String getObjectOwner() {
		return objectOwner;
	}

	public void setObjectOwner(String objectOwner) {
		this.objectOwner = objectOwner;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	@Override
	public String getObjType() {
		return OBJ_TYPE_SYNONYM;
	}
	
	public void setDDL(String createDDL) {
		this.createDDL = createDDL;
	}

	@Override
	public String getDDL() {
		return createDDL;
	}

}
