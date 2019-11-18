package com.runsim.backend.nas.impl.values;

import com.runsim.backend.utils.OctetInputStream;
import com.runsim.backend.utils.OctetOutputStream;
import com.runsim.backend.utils.octets.OctetN;

public class VServiceAreaList11 extends VServiceAreaList {
    public VMccMnc mccMnc;

    public static VServiceAreaList01 decode(OctetInputStream stream, int count) {
        var res = new VServiceAreaList01();
        res.mccMnc = VMccMnc.decode(stream);
        return res;
    }

    @Override
    public void encode(OctetOutputStream stream) {
        var flags = new OctetN(0, 1);
        flags = flags.setBitRange(0, 4, 1);
        flags = flags.setBitRange(5, 6, 0b01);
        flags = flags.setBit(7, allowedType.intValue());
        stream.writeOctets(flags);
        mccMnc.encode(stream);
    }
}