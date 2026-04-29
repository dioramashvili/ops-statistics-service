package ge.bsb.ops.statistics.handler;

import ge.bsb.ops.statistics.model.Transaction;
import ge.bsb.ops.statistics.model.TransactionMessage;
import ge.bsb.ops.statistics.parser.MessageParser;

public class MessageHandler {
    MessageParser messageParser = new MessageParser();
    void handle(TransactionMessage message){
        //Transaction transaction = messageParser.parse(message.body());
    }
}