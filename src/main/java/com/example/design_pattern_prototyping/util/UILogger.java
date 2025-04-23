package com.example.design_pattern_prototyping.util;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UILogger {

    private final TextArea logArea;
    private final Logger logger;

    public UILogger(TextArea logArea, Logger logger) {
        this.logArea = logArea;
        this.logger = logger;
    }

    public void info(String msg) {
        log(msg, Level.INFO);
    }

    public void warning(String msg) {
        log(msg, Level.WARNING);
    }

    public void error(String msg) {
        log(msg, Level.SEVERE);
    }

    public void log(String msg, Level level) {
        String timestamp = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] ";
        String logEntry = timestamp + msg;

        // Log to TextArea in JavaFX thread
        Platform.runLater(() -> {
            if (logArea != null) {
                logArea.appendText(logEntry + "\n");
            }
        });

        // Log to file/logger
        if (logger != null) {
            logger.log(level, msg);
        }
    }
}