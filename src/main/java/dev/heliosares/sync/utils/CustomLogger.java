package dev.heliosares.sync.utils;

import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class CustomLogger {
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[96m";

    public static Logger getLogger(String name) {

        Logger logger = Logger.getLogger(name);
        logger.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1$tF %1$tT] [%2$s] [%3$s] %4$s %n";

            @Override
            public synchronized String format(LogRecord record) {
                String color = switch (record.getLevel().toString()) {
                    case "INFO" -> ANSI_CYAN;
                    case "WARNING" -> ANSI_YELLOW;
                    case "SEVERE" -> ANSI_RED;
                    default -> ANSI_RESET;
                };
                return color + String.format(format, new Date(record.getMillis()), record.getLevel().getLocalizedName(), record.getLoggerName(), record.getMessage()) + ANSI_RESET;
            }
        });
        logger.addHandler(handler);

        return logger;
    }
}
