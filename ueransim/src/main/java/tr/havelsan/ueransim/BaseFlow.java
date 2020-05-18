package tr.havelsan.ueransim;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;
import tr.havelsan.ueransim.api.Messaging;
import tr.havelsan.ueransim.contexts.SimulationContext;
import tr.havelsan.ueransim.nas.core.messages.NasMessage;
import tr.havelsan.ueransim.ngap.ngap_pdu_descriptions.NGAP_PDU;
import tr.havelsan.ueransim.ngap2.NgapInternal;
import tr.havelsan.ueransim.utils.Color;
import tr.havelsan.ueransim.utils.Console;
import tr.havelsan.ueransim.utils.Json;
import tr.havelsan.ueransim.utils.Utils;

public abstract class BaseFlow {
    protected final SimulationContext ctx;
    private boolean started;
    private State currentState;

    //======================================================================================================
    //                                          CONSTRUCTORS
    //======================================================================================================

    public BaseFlow(SimulationContext simContext) {
        this.ctx = simContext;
    }

    //======================================================================================================
    //                                            LOGGING
    //======================================================================================================

    private void logReceivedMessage(IncomingMessage incomingMessage) {
        Console.printDiv();
        Console.println(Color.BLUE, "Received NGAP: %s", incomingMessage.ngapMessage.getClass().getSimpleName());
        Console.println(Color.WHITE_BRIGHT, Utils.xmlToJson(Ngap.xerEncode(incomingMessage.ngapPdu)));
        if (incomingMessage.nasMessage != null) {
            Console.println(Color.BLUE, "Received NAS: %s", incomingMessage.nasMessage.getClass().getSimpleName());
            Console.println(Color.WHITE_BRIGHT, Json.toJson(incomingMessage.nasMessage));
        }
    }

    private void logSentMessage(OutgoingMessage message) {
        Console.printDiv();
        Console.println(Color.BLUE, "Sent NGAP: %s", NgapInternal.extractNgapMessage(message.ngapPdu).getClass().getSimpleName());
        Console.println(Color.WHITE_BRIGHT, Utils.xmlToJson(Ngap.xerEncode(message.ngapPdu)));

        if (message.plainNas != null) {
            Console.println(Color.BLUE, "Sent Plain NAS: %s", message.plainNas.getClass().getSimpleName());
            Console.println(Color.WHITE_BRIGHT, Json.toJson(message.plainNas));
        }
        if (message.securedNas != null && message.plainNas != message.securedNas) {
            Console.println(Color.BLUE, "Sent Secured NAS: %s", message.securedNas.getClass().getSimpleName());
            Console.println(Color.WHITE_BRIGHT, Json.toJson(message.securedNas));
        }
    }

    protected void logUnhandledMessage(String receivedMessageName, Class<?>... expectedType) {
        if (expectedType == null || expectedType.length == 0) {
            Console.println(Color.YELLOW, "Unhandled message received: %s", receivedMessageName);
        } else {
            var sb = new StringBuilder();
            for (int i = 0; i < expectedType.length; i++) {
                sb.append(expectedType[i].getSimpleName());
                if (i != expectedType.length - 1) {
                    sb.append(',');
                }
            }

            var expectedMessages = sb.toString();
            Console.println(Color.YELLOW, "Unhandled message received: %s, expected messages were: %s", receivedMessageName, expectedMessages);
        }
    }

    protected void logUnhandledMessage(IncomingMessage message, Class<?>... expectedType) {
        var incomingMessage = message.ngapMessage.getClass().getSimpleName();
        if (message.nasMessage != null) {
            incomingMessage += "/" + message.nasMessage.getClass().getSimpleName();
        }
        logUnhandledMessage(incomingMessage, expectedType);
    }

    protected void logUnhandledMessage(NasMessage message, Class<?>... expectedType) {
        logUnhandledMessage(message.getClass().getSimpleName(), expectedType);
    }

    protected void logFlowComplete() {
        Console.println(Color.GREEN_BOLD, "%s completed", getClass().getSimpleName());
    }

    protected void logFlowFailed(String errorMessage) {
        if (errorMessage != null && errorMessage.length() > 0) {
            Console.println(Color.RED_BOLD, "%s failed: %s", getClass().getSimpleName(), errorMessage);
        } else {
            Console.println(Color.RED_BOLD, "%s failed", getClass().getSimpleName());
        }
    }

    protected void logFlowFailed() {
        logFlowFailed("");
    }

    //======================================================================================================
    //                                           MESSAGING
    //======================================================================================================

    protected final void send(SendingMessage sendingMessage) {
        var outgoing = Messaging.handleOutgoingMessage(ctx, sendingMessage);
        sendSctpData(Ngap.perEncode(outgoing.ngapPdu));
        logSentMessage(outgoing);
    }

    private void receive(NGAP_PDU ngapPdu) {
        var incomingMessage = Messaging.handleIncomingMessage(ctx, ngapPdu);
        try {
            this.currentState = this.currentState.accept(incomingMessage);
        } catch (FlowFailedException exception) {
            this.currentState = flowFailed(exception.getMessage());
        }
    }

    //======================================================================================================
    //                                            GENERAL
    //======================================================================================================

    public final void start() throws Exception {
        if (started) throw new RuntimeException("already started");
        this.started = true;

        boolean mainStepFailed = false;
        try {
            this.currentState = main(null);
        } catch (FlowFailedException exception) {
            this.currentState = flowFailed(exception.getMessage());
            mainStepFailed = true;
        }

        if (!mainStepFailed) {
            this.ctx.sctpClient.receiverLoop(this::receiveSctpData);
        }
    }

    private void receiveSctpData(byte[] receivedBytes, MessageInfo messageInfo, SctpChannel channel) {
        receive(Ngap.perDecode(NGAP_PDU.class, receivedBytes));
    }

    private void sendSctpData(byte[] data) {
        try {
            ctx.sctpClient.send(ctx.streamNumber, data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //======================================================================================================
    //                                             STATES
    //======================================================================================================

    protected final State sinkState(IncomingMessage message) {
        return this::sinkState;
    }

    public final State closeConnection() {
        ctx.sctpClient.close();
        return this::sinkState;
    }

    public final State abortReceiver() {
        ctx.sctpClient.abortReceiver();
        return this::sinkState;
    }

    public final State flowComplete() {
        logFlowComplete();
        return abortReceiver();
    }

    public final State flowFailed(String errorMessage) {
        logFlowFailed(errorMessage);
        return abortReceiver();
    }

    public final State flowFailed() {
        return flowFailed(null);
    }

    public abstract State main(IncomingMessage message) throws Exception;

    //======================================================================================================
    //                                           OTHERS
    //======================================================================================================

    @FunctionalInterface
    public interface State {
        State accept(IncomingMessage message);
    }

    protected static class FlowFailedException extends RuntimeException {
        public FlowFailedException() {
        }

        public FlowFailedException(String message) {
            super(message);
        }
    }
}
