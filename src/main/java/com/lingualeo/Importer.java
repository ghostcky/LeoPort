package com.lingualeo;

import com.lingualeo.client.ApiClient;
import com.lingualeo.client.TranslateDto;
import com.lingualeo.reader.Word;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javax.naming.AuthenticationException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Importer {
    private final List<Word> words;
    private final Label wordLabel;
    private final HBox translateBlock;
    private final HBox tlBlock;
    private final ApiClient client;
    private static final Logger logger = Logger.getLogger(Importer.class.getName());
    private ProgressBar progressBar;
    private final Object lock = new Object();
    private Word currentWord = null;
    private Map<String, TranslateDto> buttonsDTO = new HashMap<>();

    public Importer(List<Word> words, ApiClient client, ProgressBar progressBar, Label wordLabel, HBox translateBlock, HBox tlBlock) {
        this.words = words;
        this.client = client;
        this.progressBar = progressBar;
        this.progressBar.setVisible(true);
        this.wordLabel = wordLabel;
        this.translateBlock = translateBlock;
        this.tlBlock = tlBlock;
    }

    Importer(List<Word> words, ApiClient client) {
        this.words = words;
        this.client = client;
        this.wordLabel = null;
        this.translateBlock = null;
        this.tlBlock = null;
    }

    public void startImport() {
        if (words.isEmpty()) {
            logger.warning("You don't have words in the file");
            return;
        }
        logger.finest("Start import to Lingualeo...");

        try {
            client.auth();
        } catch (AuthenticationException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return;
        }

        updateProgress(0);
        float count = words.size();
        float i = 0;

        for (Word word : words) {
            try {
                List<TranslateDto> it = client.getTranslates(word.getName());

                if (it != null && it.size() > 0) {
                    if (client.getTooltip()) {
                        showWord(word, it);
                    } else {
                        processWord(word, it.get(0));
                    }
                }

                float percent = i / count;
                updateProgress(percent);

                i++;
            } catch (AuthenticationException | IOException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
            }
        }
        hideButtons();
        updateProgress(1);
        logger.finest("End import to Lingualeo...");
    }

    private void processWord(Word word, TranslateDto tr) throws AuthenticationException, IOException {
        if (tr.isUser == 1) {
            logger.finest("Word exists: " + word.getName());
        }
        client.addWord(word.getName(), tr.value, word.getContext());
        logger.finest("Word added: " + word.getName());
    }

    private void hideButtons() {
        removeBtns();
        Platform.runLater(() -> {
            tlBlock.setVisible(false);
            tlBlock.setManaged(false);
            translateBlock.setVisible(false);
            translateBlock.setManaged(false);
        });
    }

    private void showWord(Word word, List<TranslateDto> itList) {
        currentWord = word;
        VBox vbButtons = initVbox(itList);
        Platform.runLater(() -> {
            tlBlock.setVisible(true);
            tlBlock.setManaged(true);
            translateBlock.setVisible(true);
            translateBlock.setManaged(true);

            wordLabel.setText(word.getName());
            translateBlock.getChildren().add(vbButtons);
        });
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleButtonAction(javafx.event.ActionEvent e)  {
        try {
            processWord(currentWord, buttonsDTO.get(((Button)e.getSource()).getText()));
        } catch (AuthenticationException | IOException e1) {
            logger.fine(e1.getMessage());
        }
        synchronized (lock) {
            lock.notify();
        }
    }

    private VBox initVbox(List<TranslateDto> itList) {
        removeBtns();

        VBox vbButtons = new VBox();
        vbButtons.setSpacing(10);
        vbButtons.setPadding(new Insets(0, 20, 10, 20));

        for (TranslateDto it : itList) {
            if (it.isUser == 1)
                continue;
            Button btn = new Button(it.value);
            btn.setOnAction(this::handleButtonAction);
            btn.setMaxWidth(Double.MAX_VALUE);
            buttonsDTO.put(it.value, it);
            vbButtons.getChildren().add(btn);
        }

        return vbButtons;
    }

    private void removeBtns() {
        Node n = translateBlock.lookup("VBox");
        if (n != null) {
            Platform.runLater(() -> translateBlock.getChildren().remove(n));
        }
        buttonsDTO.clear();
    }

    private void updateProgress(double progress) {
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
    }
}
