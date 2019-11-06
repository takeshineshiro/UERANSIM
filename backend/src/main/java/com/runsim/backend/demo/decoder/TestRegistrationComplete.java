package com.runsim.backend.demo.decoder;

import com.runsim.backend.demo.DecoderTesting;
import com.runsim.backend.protocols.nas.impl.enums.EExtendedProtocolDiscriminator;
import com.runsim.backend.protocols.nas.impl.enums.EMessageType;
import com.runsim.backend.protocols.nas.impl.enums.ESecurityHeaderType;
import com.runsim.backend.protocols.nas.impl.messages.RegistrationComplete;
import com.runsim.backend.protocols.nas.messages.NasMessage;

public class TestRegistrationComplete extends DecoderTesting.PduTest {

    @Override
    public String getPdu() {
        return "7e0043";
    }

    @Override
    public void compare(NasMessage message) {
        assertInstance(message, RegistrationComplete.class);
        var mes = (RegistrationComplete) message;
        assertEquals(mes.messageType, EMessageType.REGISTRATION_COMPLETE);
        assertEquals(mes.extendedProtocolDiscriminator, EExtendedProtocolDiscriminator.MOBILITY_MANAGEMENT_MESSAGES);
        assertEquals(mes.securityHeaderType, ESecurityHeaderType.NOT_PROTECTED);
    }
}
