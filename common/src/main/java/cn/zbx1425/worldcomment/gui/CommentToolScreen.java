package cn.zbx1425.worldcomment.gui;

import cn.zbx1425.worldcomment.Main;
import cn.zbx1425.worldcomment.data.CommentEntry;
import cn.zbx1425.worldcomment.data.network.SubmitDispatcher;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.types.templates.Check;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CommentToolScreen extends Screen {

    private final Path imagePath;

    private static final int SQ_SIZE = 20;
    private static final int SIDEBAR_OFFSET = 100;

    private static final int CONTAINER_PADDING_X = 8;
    private static final int CONTAINER_PADDING_Y = 5;

    public CommentToolScreen(Path imagePath) {
        super(Component.literal("Comment Tool"));
        this.imagePath = imagePath;
        this.screenshotSaved = false;
        try (FileInputStream fis = new FileInputStream(imagePath.toFile())) {
            widgetImage = new WidgetUnmanagedImage(new DynamicTexture(NativeImage.read(fis)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onClose() {
        widgetImage.close();
        super.onClose();
    }

    private List<CommentTypeButton> radioButtons = new ArrayList<>();
    private WidgetUnmanagedImage widgetImage;
    private MultiLineEditBox textBoxMessage;
    private Checkbox checkBoxNoImage;
    private Checkbox checkBoxAnonymous;
    private WidgetColorButton btnSaveScreenshot;
    private Button btnSendFeedback;
    private int selectedCommentType = 0;

    boolean screenshotSaved;

    public int containerWidth, containerHeight, containerOffsetX, containerOffsetY;

    @Override
    protected void init() {
        super.init();

        Minecraft minecraft = Minecraft.getInstance();

        int baseY = CONTAINER_PADDING_Y;
        radioButtons.clear();
        boolean canAccessBuildTools = minecraft.gameMode.getPlayerMode() == GameType.CREATIVE;
        for (int r = 0; r < (canAccessBuildTools ? 2 : 1); r++) {
            addRenderableWidget(new WidgetFlagLabel(
                    SIDEBAR_OFFSET - 4, baseY, CommentTypeButton.BTN_WIDTH * 4 + 10, SQ_SIZE / 2,
                    0xFF2196F3, Component.translatable("gui.worldcomment.comment_type.r" + (r + 1))
            ));
            for (int c = 0; c < 4; c++) {
                CommentTypeButton selectBtn = new CommentTypeButton(
                        SIDEBAR_OFFSET + CommentTypeButton.BTN_WIDTH * c,
                    baseY + SQ_SIZE / 2,
                    r * 4 + c + 1, sender -> {
                        selectedCommentType = ((CommentTypeButton)sender).commentType;
                        for (CommentTypeButton radioButton : radioButtons) {
                            radioButton.active = radioButton.commentType != selectedCommentType;
                        }
                        updateBtnSendFeedback();
                    }
                );
                selectBtn.active = selectBtn.commentType != selectedCommentType;
                addRenderableWidget(selectBtn);
                radioButtons.add(selectBtn);
            }
            baseY += CommentTypeButton.BTN_HEIGHT + SQ_SIZE / 2;
        }
        if (!canAccessBuildTools) {
            baseY += SQ_SIZE / 2;
        }

        addRenderableWidget(new WidgetFlagLabel(
                SIDEBAR_OFFSET - 4, baseY, CommentTypeButton.BTN_WIDTH * 5 + 10, SQ_SIZE / 2,
                0xFF00BCD4, Component.translatable("gui.worldcomment.message")
        ));
        baseY += SQ_SIZE / 2;
        textBoxMessage = new MultiLineEditBox(
                Minecraft.getInstance().font,
                SIDEBAR_OFFSET, baseY, CommentTypeButton.BTN_WIDTH * 5, SQ_SIZE * 4,
                Component.translatable("gui.worldcomment.message.placeholder"),
                Component.literal("")
        );
        textBoxMessage.setValueListener(ignored -> updateBtnSendFeedback());
        addRenderableWidget(textBoxMessage);
        baseY += textBoxMessage.getHeight();
        baseY += CONTAINER_PADDING_Y;

        btnSendFeedback = new WidgetColorButton(
                SIDEBAR_OFFSET + CommentTypeButton.BTN_WIDTH * 3, baseY, CommentTypeButton.BTN_WIDTH * 2, SQ_SIZE,
                Component.translatable("gui.worldcomment.submit"), 0xFFC5E1A5,
                sender -> sendReport()
        );
        updateBtnSendFeedback();
        addRenderableWidget(btnSendFeedback);

        baseY = CONTAINER_PADDING_Y;
        widgetImage.setBounds(0, baseY, SIDEBAR_OFFSET - SQ_SIZE);
        addRenderableWidget(widgetImage);
        baseY += widgetImage.getHeight() + SQ_SIZE / 2;
        checkBoxNoImage = new Checkbox(
                0, baseY, SIDEBAR_OFFSET - SQ_SIZE, SQ_SIZE,
                Component.translatable("gui.worldcomment.exclude_screenshot"),
                false, true
        );
        addRenderableWidget(checkBoxNoImage);
        baseY += SQ_SIZE;
        checkBoxAnonymous = new Checkbox(
                0, baseY, SIDEBAR_OFFSET - SQ_SIZE, SQ_SIZE,
                Component.translatable("gui.worldcomment.anonymous"),
                false, true
        );
        addRenderableWidget(checkBoxAnonymous);

        int maxX = 0, maxY = 0;
        for (GuiEventListener child : children()) {
            AbstractWidget widget = (AbstractWidget)child;
            maxX = Math.max(maxX, widget.getX() + widget.getWidth());
            maxY = Math.max(maxY, widget.getY() + widget.getHeight());
        }
        containerWidth = maxX;
        containerHeight = maxY;

        addRenderableWidget(new WidgetColorButton(
                maxX - SQ_SIZE, 0, SQ_SIZE, SQ_SIZE, Component.literal("X"), 0xFFEF9A9A,
                sender -> onClose()
        ));

        containerOffsetX = (width - (containerWidth + CONTAINER_PADDING_X * 2)) / 2 + CONTAINER_PADDING_X;
        containerOffsetY = (height - (containerHeight + CONTAINER_PADDING_Y * 2)) / 2 + CONTAINER_PADDING_Y;
        for (GuiEventListener child : children()) {
            AbstractWidget widget = (AbstractWidget)child;
            widget.setX(widget.getX() + containerOffsetX);
            widget.setY(widget.getY() + containerOffsetY);
        }

        btnSaveScreenshot = new WidgetColorButton(
                containerOffsetX, btnSendFeedback.getY(), CommentTypeButton.BTN_WIDTH * 2, SQ_SIZE,
                Component.translatable("gui.worldcomment.save_screenshot"), 0xFF81D4FA, sender -> {
                    Path persistentPath = imagePath.resolveSibling(
                            imagePath.getFileName().toString().replace("WorldComment", "WorldComment-Saved"));
                    try {
                        Files.copy(imagePath, persistentPath);
                        screenshotSaved = true;
                        btnSaveScreenshot.active = false;
                    } catch (IOException e) {
                        Main.LOGGER.error("Copy image", e);
                    }
                }
        );
        btnSaveScreenshot.active = !screenshotSaved;
        addRenderableWidget(btnSaveScreenshot);
    }

    private void sendReport() {
        if (selectedCommentType == 0) return;
        Minecraft.getInstance().execute(() -> {
            CommentEntry comment = new CommentEntry(
                    Minecraft.getInstance().player, checkBoxAnonymous.selected(),
                    selectedCommentType, textBoxMessage.getValue()
            );
            long jobId = SubmitDispatcher.addJob(
                    comment, checkBoxNoImage.selected() ? null : imagePath,
                    data -> Minecraft.getInstance().execute(() -> {
                        if (data == null) {
                            Minecraft.getInstance().player.displayClientMessage(
                                    Component.translatable("gui.worldcomment.send_finish"), false);
                        } else {
                            if (data.exception != null) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.translatable("gui.worldcomment.send_fail", data.exception.getMessage()), false);
                            } else {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.translatable("gui.worldcomment.send_upload_incomplete"), false);
                            }
                        }
                    }
            ));
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("gui.worldcomment.send_pending"), false);
            ItemStack item = Minecraft.getInstance().player.getMainHandItem();
            if (item.is(Main.ITEM_COMMENT_TOOL.get())) {
                item.getOrCreateTag().putLong("uploadJobId", jobId);
            }
        });
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.pose().pushPose();
        setupAnimationTransform(guiGraphics);

        guiGraphics.fill(
                containerOffsetX - CONTAINER_PADDING_X,
                containerOffsetY - CONTAINER_PADDING_Y,
                containerOffsetX + containerWidth + CONTAINER_PADDING_X,
                containerOffsetY + containerHeight - SQ_SIZE + CONTAINER_PADDING_Y,
                0xBB111111
        );
        guiGraphics.fill(
                containerOffsetX - CONTAINER_PADDING_X,
                containerOffsetY + containerHeight - SQ_SIZE + CONTAINER_PADDING_Y,
                containerOffsetX + containerWidth + CONTAINER_PADDING_X,
                containerOffsetY + containerHeight + CONTAINER_PADDING_Y,
                0xBB444444
        );
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().popPose();
    }

    private long timestampOpenGui = 0L;

    private void setupAnimationTransform(GuiGraphics guiGraphics) {
        long timestampNow = System.currentTimeMillis();
        if (timestampOpenGui == 0) timestampOpenGui = timestampNow;

        float animProgress = Mth.clamp((timestampNow - timestampOpenGui) / 400f, 0f, 1f);
        float x1 = Mth.lerp(animProgress, 0, containerOffsetX);
        float x2 = Mth.lerp(animProgress, width, containerOffsetX + widgetImage.getWidth());
        float y1 = Mth.lerp(animProgress, 0, containerOffsetY + CONTAINER_PADDING_Y);
        float y2 = Mth.lerp(animProgress, height, containerOffsetY + CONTAINER_PADDING_Y + widgetImage.getHeight());

        float scaleX = (x2 - x1) / widgetImage.getWidth();
        float scaleY = (y2 - y1) / widgetImage.getHeight();
        guiGraphics.pose().translate(x1, y1, 0);
        guiGraphics.pose().scale(scaleX, scaleY, 1);
        guiGraphics.pose().translate(-containerOffsetX, -(containerOffsetY + CONTAINER_PADDING_Y), 0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateBtnSendFeedback() {
        if (selectedCommentType == 0) {
            btnSendFeedback.active = false;
            btnSendFeedback.setTooltip(Tooltip.create(
                    Component.translatable("gui.worldcomment.require_comment_type").withStyle(ChatFormatting.RED)));
        } else if (textBoxMessage.getValue().length() > CommentEntry.MESSAGE_MAX_LENGTH) {
            btnSendFeedback.active = false;
            btnSendFeedback.setTooltip(Tooltip.create(
                    Component.translatable("gui.worldcomment.message_too_long").withStyle(ChatFormatting.RED)));
        } else {
            btnSendFeedback.active = true;
            btnSendFeedback.setTooltip(null);
        }
    }
}
