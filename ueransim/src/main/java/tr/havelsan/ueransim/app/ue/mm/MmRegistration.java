/*
 * MIT License
 *
 * Copyright (c) 2020 ALİ GÜNGÖR
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package tr.havelsan.ueransim.app.ue.mm;

import tr.havelsan.ueransim.app.common.enums.EMmState;
import tr.havelsan.ueransim.app.common.enums.EMmSubState;
import tr.havelsan.ueransim.app.common.enums.ERmState;
import tr.havelsan.ueransim.app.common.simctx.UeSimContext;
import tr.havelsan.ueransim.core.exceptions.NotImplementedException;
import tr.havelsan.ueransim.nas.eap.Eap;
import tr.havelsan.ueransim.nas.impl.enums.*;
import tr.havelsan.ueransim.nas.impl.ies.*;
import tr.havelsan.ueransim.nas.impl.messages.RegistrationAccept;
import tr.havelsan.ueransim.nas.impl.messages.RegistrationComplete;
import tr.havelsan.ueransim.nas.impl.messages.RegistrationReject;
import tr.havelsan.ueransim.nas.impl.messages.RegistrationRequest;
import tr.havelsan.ueransim.utils.Tag;
import tr.havelsan.ueransim.utils.console.Log;

public class MmRegistration {

    public static void sendRegistration(UeSimContext ctx, ERegistrationType registrationType, EFollowOnRequest followOn) {
        Log.funcIn("Starting: Registration procedure (%s)", registrationType);

        MobilityManagement.switchState(ctx, EMmState.MM_REGISTERED_INITIATED, EMmSubState.MM_REGISTERED_INITIATED__NA);

        var ngKsi = new IENasKeySetIdentifier(ETypeOfSecurityContext.NATIVE_SECURITY_CONTEXT, IENasKeySetIdentifier.NOT_AVAILABLE_OR_RESERVED);
        if (ctx.currentNsCtx != null && ctx.currentNsCtx.ngKsi != null) {
            ngKsi = ctx.currentNsCtx.ngKsi;
        }

        var registrationRequest = new RegistrationRequest();
        registrationRequest.registrationType = new IE5gsRegistrationType(followOn, registrationType);
        registrationRequest.nasKeySetIdentifier = ngKsi;
        registrationRequest.requestedNSSAI = new IENssai(ctx.ueConfig.requestedNssai);
        registrationRequest.ueSecurityCapability = MmSecurity.createSecurityCapabilityIe();
        registrationRequest.updateType = new IE5gsUpdateType(
                ctx.ueConfig.smsOverNasSupported ? IE5gsUpdateType.ESmsRequested.SUPPORTED : IE5gsUpdateType.ESmsRequested.NOT_SUPPORTED,
                IE5gsUpdateType.ENgRanRadioCapabilityUpdate.NOT_NEEDED);

        if (!registrationType.equals(ERegistrationType.PERIODIC_REGISTRATION_UPDATING)) {
            registrationRequest.mmCapability = new IE5gMmCapability();
            registrationRequest.mmCapability.s1Mode = EEpcNasSupported.NOT_SUPPORTED;
            registrationRequest.mmCapability.hoAttach = EHandoverAttachSupported.NOT_SUPPORTED;
            registrationRequest.mmCapability.lpp = ELtePositioningProtocolCapability.NOT_SUPPORTED;
        }

        if (ctx.mmCtx.storedGuti != null) {
            registrationRequest.mobileIdentity = ctx.mmCtx.storedGuti;
        } else {
            var suci = MmIdentity.getOrGenerateSuci(ctx);
            if (suci != null) {
                registrationRequest.mobileIdentity = suci;

                if (!ctx.ueTimers.t3519.isRunning()) {
                    ctx.ueTimers.t3519.start();
                }

            } else {
                registrationRequest.mobileIdentity = new IEImeiMobileIdentity(ctx.ueConfig.imei);
            }
        }

        if (ctx.mmCtx.lastVisitedRegisteredTai != null) {
            registrationRequest.lastVisitedRegisteredTai = ctx.mmCtx.lastVisitedRegisteredTai;
        }

        ctx.mmCtx.registrationRequest = registrationRequest;

        ctx.ueTimers.t3510.start();
        ctx.ueTimers.t3502.stop();
        ctx.ueTimers.t3511.stop();

        MobilityManagement.sendMm(ctx, registrationRequest);

        Log.funcOut();
    }

    public static void receiveRegistrationAccept(UeSimContext ctx, RegistrationAccept message) {
        Log.funcIn("Handling: Registration Accept");

        boolean sendCompleteMes = false;

        ctx.mmCtx.taiList = message.taiList;

        if (message.t3512Value != null && message.t3512Value.hasValue()) {
            ctx.ueTimers.t3512.start(message.t3512Value);
        }

        if (message.mobileIdentity instanceof IE5gGutiMobileIdentity) {
            ctx.mmCtx.storedGuti = (IE5gGutiMobileIdentity) message.mobileIdentity;
            ctx.ueTimers.t3519.stop();

            sendCompleteMes = true;
        }

        if (sendCompleteMes) {
            MobilityManagement.sendMm(ctx, new RegistrationComplete());
        }

        MobilityManagement.switchState(ctx, ERmState.RM_REGISTERED);
        MobilityManagement.switchState(ctx, EMmState.MM_REGISTERED, EMmSubState.MM_REGISTERED__NORMAL_SERVICE);

        Log.success(Tag.PROCEDURE_RESULT, "Registration is successful");
        Log.funcOut();
    }

    public static void receiveRegistrationReject(UeSimContext ctx, RegistrationReject message) {
        Log.funcIn("Handling: Registration reject");

        Log.error(Tag.PROCEDURE_RESULT, "Registration failed");

        if (message.eapMessage != null) {
            if (message.eapMessage.eap.code.equals(Eap.ECode.FAILURE)) {
                MmAuthentication.receiveEapFailureMessage(ctx, message.eapMessage.eap);
            } else {
                Log.warning(Tag.PROC, "network sent EAP with type of %s in RegistrationReject, ignoring EAP IE.",
                        message.eapMessage.eap.code.name());
            }
        }

        var cause = message.mmCause.value;

        var regType = ctx.mmCtx.registrationRequest.registrationType.registrationType;

        if (regType.equals(ERegistrationType.INITIAL_REGISTRATION)) {
            if (cause.equals(EMmCause.ILLEGAL_UE) || cause.equals(EMmCause.ILLEGAL_ME)) {
                ctx.mmCtx.storedGuti = null;
                ctx.mmCtx.lastVisitedRegisteredTai = null;
                ctx.mmCtx.taiList = null;
                ctx.currentNsCtx = null;
                ctx.nonCurrentNsCtx = null;
                MobilityManagement.switchState(ctx, EMmState.MM_DEREGISTERED, EMmSubState.MM_DEREGISTERED__PLMN_SEARCH);
            } else if (cause.equals(EMmCause.FIVEG_SERVICES_NOT_ALLOWED)) {
                ctx.mmCtx.storedGuti = null;
                ctx.mmCtx.lastVisitedRegisteredTai = null;
                ctx.mmCtx.taiList = null;
                ctx.currentNsCtx = null;
                ctx.nonCurrentNsCtx = null;
                MobilityManagement.switchState(ctx, EMmState.MM_DEREGISTERED, EMmSubState.MM_DEREGISTERED__PLMN_SEARCH);
            } else if (cause.equals(EMmCause.PLMN_NOT_ALLOWED)) {
                ctx.mmCtx.storedGuti = null;
                ctx.mmCtx.lastVisitedRegisteredTai = null;
                ctx.mmCtx.taiList = null;
                ctx.currentNsCtx = null;
                ctx.nonCurrentNsCtx = null;
                MobilityManagement.switchState(ctx, EMmState.MM_DEREGISTERED, EMmSubState.MM_DEREGISTERED__PLMN_SEARCH);
            } else if (cause.equals(EMmCause.TA_NOT_ALLOWED)) {
                ctx.mmCtx.storedGuti = null;
                ctx.mmCtx.lastVisitedRegisteredTai = null;
                ctx.mmCtx.taiList = null;
                ctx.currentNsCtx = null;
                ctx.nonCurrentNsCtx = null;
                MobilityManagement.switchState(ctx, EMmState.MM_DEREGISTERED, EMmSubState.MM_DEREGISTERED__LIMITED_SERVICE);
            } else if (cause.equals(EMmCause.ROAMING_NOT_ALLOWED_IN_TA)) {
                ctx.mmCtx.storedGuti = null;
                ctx.mmCtx.lastVisitedRegisteredTai = null;
                ctx.mmCtx.taiList = null;
                ctx.currentNsCtx = null;
                ctx.nonCurrentNsCtx = null;
                MobilityManagement.switchState(ctx, EMmState.MM_DEREGISTERED, EMmSubState.MM_DEREGISTERED__LIMITED_SERVICE);
            } else if (cause.equals(EMmCause.NO_SUITIBLE_CELLS_IN_TA)) {
                ctx.mmCtx.storedGuti = null;
                ctx.mmCtx.lastVisitedRegisteredTai = null;
                ctx.mmCtx.taiList = null;
                ctx.currentNsCtx = null;
                ctx.nonCurrentNsCtx = null;
                MobilityManagement.switchState(ctx, EMmState.MM_DEREGISTERED, EMmSubState.MM_DEREGISTERED__LIMITED_SERVICE);
            } else if (cause.equals(EMmCause.CONGESTION)) {
                if (message.t3346value != null && message.t3346value.hasValue()) {

                    MobilityManagement.switchState(ctx, EMmState.MM_DEREGISTERED, EMmSubState.MM_DEREGISTERED__ATTEMPTING_REGISTRATION);
                    ctx.ueTimers.t3346.stop();

                    if (message.securityHeaderType.isIntegrityProtected()) {
                        ctx.ueTimers.t3346.start(message.t3346value);
                    } else {
                        ctx.ueTimers.t3346.start(new IEGprsTimer2(5));
                    }

                } else {
                    // todo abnormal case see 5.5.1.2.7.
                    throw new NotImplementedException("");
                }
            } else if (cause.equals(EMmCause.N1_MODE_NOT_ALLOWED)) {
                ctx.mmCtx.storedGuti = null;
                ctx.mmCtx.lastVisitedRegisteredTai = null;
                ctx.mmCtx.taiList = null;
                ctx.currentNsCtx = null;
                ctx.nonCurrentNsCtx = null;
                MobilityManagement.switchState(ctx, EMmState.MM_NULL, EMmSubState.MM_NULL__NA);
            } else if (cause.equals(EMmCause.NON_3GPP_ACCESS_TO_CN_NOT_ALLOWED)) {
                // todo
                throw new NotImplementedException("");
            } else if (cause.equals(EMmCause.SERVING_NETWORK_NOT_AUTHORIZED)) {
                // todo
                throw new NotImplementedException("");
            } else {
                // todo
                throw new NotImplementedException("");
            }
        } else if (regType.equals(ERegistrationType.EMERGENCY_REGISTRATION)) {
            if (cause.equals(EMmCause.PEI_NOT_ACCEPTED)) {
                // todo
                throw new NotImplementedException("");
            } else {
                // todo: abnormal case
                throw new NotImplementedException("");
            }
        } else {
            // todo
            throw new NotImplementedException("");
        }

        MobilityManagement.switchState(ctx, ERmState.RM_DEREGISTERED);

        Log.funcOut();
    }
}
