package com.syncmod.client.screen;

import com.syncmod.client.ClientEvents;
import com.syncmod.client.sync.PlannedMod;
import com.syncmod.client.sync.RestartHelper;
import com.syncmod.client.sync.SyncService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class SyncScreen extends Screen {
    private static final int BAR_WIDTH = 260;
    private static final int BAR_HEIGHT = 12;
    private static final int MAX_LISTED = 8;

    private final SyncService service;
    private final Screen fallback;
    private SyncService.Phase shownPhase;

    public SyncScreen(SyncService service) {
        this(service, null);
    }

    public SyncScreen(SyncService service, Screen fallback) {
        super(Component.translatable("syncmod.screen.title"));
        this.service = service;
        this.fallback = fallback;
        this.shownPhase = service.phase();
    }

    @Override
    protected void init() {
        shownPhase = service.phase();
        int centerX = width / 2;
        int y = height - 36;
        switch (shownPhase) {
            case CHECKING -> addRenderableWidget(Button.builder(Component.translatable("syncmod.button.cancel"), button -> backToMenu())
                    .bounds(centerX - 50, y, 100, 20).build());
            case UNAVAILABLE -> {
                if (fallback != null) {
                    addRenderableWidget(Button.builder(Component.translatable("syncmod.button.details"), button -> {
                        ClientEvents.allowNextMismatchScreen();
                        minecraft.setScreen(fallback);
                    }).bounds(centerX - 105, y, 100, 20).build());
                    addRenderableWidget(Button.builder(Component.translatable("syncmod.button.back"), button -> backToMenu())
                            .bounds(centerX + 5, y, 100, 20).build());
                } else {
                    addRenderableWidget(Button.builder(Component.translatable("syncmod.button.back"), button -> backToMenu())
                            .bounds(centerX - 100, y, 200, 20).build());
                }
            }
            case CONFIRM -> {
                addRenderableWidget(Button.builder(Component.translatable("syncmod.button.download"), button -> service.start())
                        .bounds(centerX - 105, y, 100, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("syncmod.button.cancel"), button -> backToMenu())
                        .bounds(centerX + 5, y, 100, 20).build());
            }
            case DOWNLOADING -> addRenderableWidget(Button.builder(Component.translatable("syncmod.button.cancel"), button -> {
                service.cancel();
                backToMenu();
            }).bounds(centerX - 50, y, 100, 20).build());
            case FAILED -> addRenderableWidget(Button.builder(Component.translatable("syncmod.button.back"), button -> backToMenu())
                    .bounds(centerX - 100, y, 200, 20).build());
            case DONE -> addRenderableWidget(Button.builder(Component.translatable("syncmod.button.restart"), button -> restart())
                    .bounds(centerX - 100, y, 200, 20).build());
            case INSTALLING -> {
            }
        }
    }

    @Override
    public void tick() {
        if (service.phase() != shownPhase) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, 20, 0xFFFFFF);
        switch (service.phase()) {
            case CHECKING -> graphics.drawCenteredString(font, Component.translatable("syncmod.status.checking"), centerX, height / 2 - 10, 0xFFFFFF);
            case UNAVAILABLE -> {
                graphics.drawCenteredString(font, Component.translatable("syncmod.unavailable.title"), centerX, height / 2 - 40, 0xFFAA00);
                Component reason = service.error();
                if (reason != null) {
                    drawWrapped(graphics, reason, centerX, height / 2 - 24, 0xFFFFFF);
                }
            }
            case CONFIRM -> renderConfirm(graphics, centerX);
            case DOWNLOADING -> renderProgress(graphics, centerX, Component.translatable(
                    "syncmod.status.downloading", service.index() + 1, service.total()));
            case INSTALLING -> renderProgress(graphics, centerX, Component.translatable("syncmod.status.installing"));
            case FAILED -> renderFailed(graphics, centerX);
            case DONE -> drawWrapped(graphics, Component.translatable("syncmod.done"), centerX, height / 2 - 20, 0x55FF55);
        }
    }

    private void renderConfirm(GuiGraphics graphics, int centerX) {
        int y = 50;
        y = drawWrapped(graphics, Component.translatable("syncmod.confirm.summary",
                service.plan().missingCount(), service.plan().outdatedCount()), centerX, y, 0xFFFFFF);
        y = drawWrapped(graphics, Component.translatable("syncmod.confirm.sources",
                String.join(", ", service.hosts())), centerX, y + 4, 0xAAAAAA);
        y += 8;
        List<PlannedMod> mods = service.plan().mods();
        for (int i = 0; i < Math.min(MAX_LISTED, mods.size()); i++) {
            graphics.drawCenteredString(font, mods.get(i).entry().filename(), centerX, y, 0xDDDDDD);
            y += font.lineHeight + 2;
        }
        if (mods.size() > MAX_LISTED) {
            graphics.drawCenteredString(font, "+" + (mods.size() - MAX_LISTED), centerX, y, 0xAAAAAA);
            y += font.lineHeight + 2;
        }
        drawWrapped(graphics, Component.translatable("syncmod.confirm.warning"), centerX, y + 8, 0xFFAA00);
    }

    private void renderProgress(GuiGraphics graphics, int centerX, Component status) {
        int y = height / 2 - 30;
        graphics.drawCenteredString(font, Component.translatable("syncmod.status.current", service.currentMod()), centerX, y, 0xFFFFFF);
        y += 18;
        int x = centerX - BAR_WIDTH / 2;
        double ratio = Math.max(0.0, Math.min(1.0, service.progress()));
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xFFFFFFFF);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF202020);
        graphics.fill(x, y, x + (int) (BAR_WIDTH * ratio), y + BAR_HEIGHT, 0xFF55FF55);
        y += BAR_HEIGHT + 6;
        graphics.drawCenteredString(font, Math.round(ratio * 100) + "%", centerX, y, 0xFFFFFF);
        graphics.drawCenteredString(font, status, centerX, y + font.lineHeight + 4, 0xAAAAAA);
    }

    private void renderFailed(GuiGraphics graphics, int centerX) {
        int y = height / 2 - 30;
        graphics.drawCenteredString(font, Component.translatable("syncmod.failed.title"), centerX, y, 0xFF5555);
        Component error = service.error();
        if (error != null) {
            drawWrapped(graphics, error, centerX, y + 18, 0xFFFFFF);
        }
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        int next = y;
        for (FormattedCharSequence line : font.split(text, width - 60)) {
            graphics.drawCenteredString(font, line, centerX, next, color);
            next += font.lineHeight + 2;
        }
        return next;
    }

    private void backToMenu() {
        minecraft.setScreen(new TitleScreen());
    }

    private void restart() {
        RestartHelper.requestRelaunch();
        minecraft.stop();
    }
}
