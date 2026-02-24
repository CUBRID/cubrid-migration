package com.cubrid.cubridmigration.tibero.trans;

import com.cubrid.cubridmigration.core.mapping.AbstractDataTypeMappingHelper;
import com.cubrid.cubridmigration.cubrid.trans.ToCUBRIDDataConverterFacade;

public class Tibero2CUBRIDTransformHelper extends Tibero2CUBRIDTranformHelper {

    public Tibero2CUBRIDTransformHelper(
            AbstractDataTypeMappingHelper dataTypeMapping, ToCUBRIDDataConverterFacade cf) {
        super(dataTypeMapping, cf);
    }
}
