/*
 * Copyright (c) 2016 CUBRID Corporation.
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

package com.cubrid.cubridmigration.informix.trans;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.mapping.AbstractDataTypeMappingHelper;
import com.cubrid.cubridmigration.core.trans.DBTransformHelper;
import com.cubrid.cubridmigration.cubrid.trans.ToCUBRIDDataConverterFacade;

/**
 * Informix2CUBRIDTransformHelper Description
 *
 * @author rathana
 * @version 1.0 
 * @created Aug 18, 2022
 */
public class Informix2CUBRIDTransformHelper extends DBTransformHelper{

	public Informix2CUBRIDTransformHelper(AbstractDataTypeMappingHelper dataTypeMapping,
			ToCUBRIDDataConverterFacade cf) {
		super(dataTypeMapping, cf);
		
	}

	@Override
	protected void adjustPrecision(Column srcColumn, Column cubridColumn, MigrationConfiguration config) {
		// TODO Auto-generated method stub
		if("decimal".equals(srcColumn.getDataType())) {
			if(cubridColumn.getPrecision()>38) {
				cubridColumn.setPrecision(38);
			}
			if(cubridColumn.getScale()>38) {
				cubridColumn.setScale(38);
			}
		}
		if("bit varying".equals(cubridColumn.getDataType())) {
			if(cubridColumn.getPrecision()>1073741823) {
				cubridColumn.setPrecision(1073741823);
			}
		}
	}

}
