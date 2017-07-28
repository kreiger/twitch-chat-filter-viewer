package com.linuxgods.kreiger.capricious.twitch.chat.gui;

import com.linuxgods.kreiger.capricious.twitch.chat.TwitchChatMessage;
import com.linuxgods.kreiger.capricious.twitch.web.TwitchWebScraper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import one.util.streamex.StreamEx;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.stream.Collectors.toList;
import static javafx.scene.layout.BackgroundPosition.CENTER;
import static javafx.scene.layout.BackgroundRepeat.NO_REPEAT;

public class TwitchChatGui {
    private static final int FONT_SIZE = 32;
    private static final int WIDTH = FONT_SIZE * 20;
    private static final double HEIGHT = Screen.getPrimary().getVisualBounds().getHeight();
    private final MaximizedFullscreenStage maximizedFullscreenStage = new MaximizedFullscreenStage();
    private final ExpiringTextFlow textFlow = createTextFlow();
    private final AutoScrollingPane autoScrollingPane = new AutoScrollingPane(textFlow);
    private final String channel;

    public TwitchChatGui(String channel, TwitchWebScraper twitchWebScraper) {
        this.channel = channel;
        maximizedFullscreenStage.setTitle(this.channel);
        Scene scene = new Scene(autoScrollingPane, WIDTH, HEIGHT);
        maximizedFullscreenStage.setScene(scene);
        maximizedFullscreenStage.show();

        setWindowIconToChannelImage(channel, twitchWebScraper);
        setTitleAndBackgroundAndStopExpiringTextsWhenScrollingIsPaused();
        fullScreenOnDoubleClick();
        setExitOnClose();
    }

    private void setExitOnClose() {
        maximizedFullscreenStage.setOnCloseRequest(event -> exit());
    }

    private void exit() {
        Platform.exit();
        System.exit(0);
    }

    private void setWindowIconToChannelImage(String channel, TwitchWebScraper twitchWebScraper) {
        CompletableFuture.supplyAsync(() -> twitchWebScraper.getChannelImage(channel).get())
                .thenAccept(image -> Platform.runLater(() -> maximizedFullscreenStage.getIcons().add(image)));
    }

    private void setTitleAndBackgroundAndStopExpiringTextsWhenScrollingIsPaused() {
        BackgroundSize backgroundSize = new BackgroundSize(BackgroundSize.AUTO, 0.4, true, true, false, false);
        Background pauseBackground = new Background(new BackgroundImage(new Image(getClass().getResourceAsStream("pause.png")), NO_REPEAT, NO_REPEAT, CENTER, backgroundSize));
        autoScrollingPane.scrollingPausedProperty().addListener((observable, oldValue, scrollPaused) -> {
            Platform.runLater(() -> maximizedFullscreenStage.setTitle(scrollPaused ? channel + " (Paused)" : channel));
            textFlow.setExpiringEnabled(!scrollPaused);
            autoScrollingPane.getViewPort().setBackground(scrollPaused ? pauseBackground : null);
        });
    }

    private ExpiringTextFlow createTextFlow() {
        ExpiringTextFlow textFlow = new ExpiringTextFlow();
        textFlow.setPadding(new Insets(20));
        textFlow.setLineSpacing(20);
        return textFlow;
    }

    private void fullScreenOnDoubleClick() {
        SingleOrDoubleClickMouseEventHandler
                .on(maximizedFullscreenStage.getScene())
                .setOnDoubleClick(event -> maximizedFullscreenStage.toggleMaximized());
    }

    public void append(TwitchChatMessage message) {
        List<Node> chatMessageTextsAndImages = createChatMessageTextsAndImages(message);
        Platform.runLater(() -> textFlow.append(chatMessageTextsAndImages));
    }

    private List<Node> createChatMessageTextsAndImages(TwitchChatMessage twitchChatMessage) {
        return StreamEx.of(twitchChatMessage.stream())
                .append(new TwitchChatMessage.Text(twitchChatMessage.toString().length(), "\n"))
                .map(part -> part.accept(new NodeFactory(twitchChatMessage)))
                .collect(toList());
    }

    private static class NodeFactory implements TwitchChatMessage.Visitor<Node> {
        private final TwitchChatMessage twitchChatMessage;

        NodeFactory(TwitchChatMessage twitchChatMessage) {
            this.twitchChatMessage = twitchChatMessage;
        }

        @Override
        public Node visitText(TwitchChatMessage.Text message) {
            return createText(message);
        }

        private Node createText(TwitchChatMessage.Text message) {
            Text text = new Text(message.toString());
            text.setFont(new Font(FONT_SIZE));
            twitchChatMessage.getColor().map(Color::web).ifPresent(text::setFill);
            return text;
        }

        @Override
        public Node visitEmote(TwitchChatMessage.Emote emote) {
            return createImage(emote.getUrl());
        }

        private Node createImage(String url) {
            ImageView imageView = new ImageView(url);
            imageView.setCache(true);
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(FONT_SIZE);
            return imageView;
        }
    }
}