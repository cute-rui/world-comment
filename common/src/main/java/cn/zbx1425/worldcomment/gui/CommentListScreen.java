package cn.zbx1425.worldcomment.gui;

import cn.zbx1425.worldcomment.Main;
import cn.zbx1425.worldcomment.data.CommentEntry;
import cn.zbx1425.worldcomment.data.ServerWorldData;
import cn.zbx1425.worldcomment.data.client.ClientWorldData;
import cn.zbx1425.worldcomment.data.client.ClientRayPicking;
import cn.zbx1425.worldcomment.data.network.ImageDownload;
import cn.zbx1425.worldcomment.network.PacketCollectionRequestC2S;
import cn.zbx1425.worldcomment.network.PacketEntryActionC2S;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.*;

public class CommentListScreen extends Screen implements IGuiCommon {

    protected CommentListScreen(CommentEntry commentForDetail) {
        super(Component.literal(""));
        this.commentForDetail = commentForDetail;
        useSubScreen(commentForDetail != null ? 3 : 0);
        prevSubScreen = commentForDetail != null ? 3 : 0;
    }

    int subScreen = 0, prevSubScreen = 0;

    Button[] pageButtons = new Button[4];

    List<CommentEntry> commentList = new ArrayList<>();
    int commentListOffset = 0;
    int latestCommentsRequestedAmount = 0;
    private final int latestCommentsPageSize = 20;

    CommentEntry commentForDetail;
    CommentEntry commentToDelete;

    private final Map<CommentEntry, WidgetCommentEntry> widgets = new WeakHashMap<>();

    private WidgetCommentEntry getWidget(CommentEntry entry) {
        return widgets.computeIfAbsent(entry, WidgetCommentEntry::new);
    }

    @Override
    protected void init() {
        super.init();
        pageButtons[0] = addRenderableWidget(Button.builder(Component.translatable("gui.worldcomment.list.nearby_posts"), sender -> useSubScreen(0))
                .bounds(10, 40, 80, 20).build());
        pageButtons[1] = addRenderableWidget(Button.builder(Component.translatable("gui.worldcomment.list.recent_posts"), sender -> useSubScreen(1))
                .bounds(10, 64, 80, 20).build());
        pageButtons[2] = addRenderableWidget(Button.builder(Component.translatable("gui.worldcomment.list.my_posts"), sender -> useSubScreen(2))
                .bounds(10, 88, 80, 20).build());
        pageButtons[3] = addRenderableWidget(Button.builder(Component.translatable("gui.worldcomment.list.detail"), sender -> {})
                .bounds(10, 122, 80, 20).build());
        for (int i = 0; i < pageButtons.length; i++) {
            pageButtons[i].active = i != subScreen;
        }
        pageButtons[3].visible = subScreen == 3;
    }

    void useSubScreen(int subScreen) {
        Minecraft minecraft = Minecraft.getInstance();
        this.prevSubScreen = this.subScreen;
        this.subScreen = subScreen;
        if (prevSubScreen != 3 || commentList.isEmpty()) {
            switch (subScreen) {
                case 0 -> {
                    commentList.clear();
                    BlockPos playerPos = minecraft.player.blockPosition();
                    for (Map<BlockPos, List<CommentEntry>> region : ClientWorldData.INSTANCE.regions.values()) {
                        for (Map.Entry<BlockPos, List<CommentEntry>> blockData : region.entrySet()) {
                            for (CommentEntry comment : blockData.getValue()) {
                                if (comment.deleted) continue;
                                commentList.add(comment);
                            }
                        }
                    }
                    commentList.sort(Comparator.comparingDouble(entry -> entry.location.distSqr(playerPos)));
                    commentListOffset = 0;
                }
                case 1 -> {
                    commentList.clear();
                    commentListOffset = 0;
                    lastRequestNonce = ServerWorldData.SNOWFLAKE.nextId();
                    latestCommentsRequestedAmount = 0;
                    PacketCollectionRequestC2S.ClientLogics.sendLatest(
                            latestCommentsRequestedAmount,  latestCommentsPageSize, lastRequestNonce);
                    latestCommentsRequestedAmount += latestCommentsPageSize;
                }
                case 2 -> {
                    commentList.clear();
                    commentListOffset = 0;
                    lastRequestNonce = ServerWorldData.SNOWFLAKE.nextId();
                    PacketCollectionRequestC2S.ClientLogics.sendPlayer(
                            minecraft.player.getGameProfile().getId(), lastRequestNonce);
                }
            }
        }
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        renderBackground(guiGraphics);
        guiGraphics.drawString(minecraft.font, Component.translatable("gui.worldcomment.list.title"),
                40, 15, 0xFFFFE6C0, true);

        if (subScreen == 3) {
            CommentEntry comment = commentForDetail;

            int picWidth = width - 100 - 20 - 20;
            int picHeight = picWidth * 9 / 16;
            int x1 = 100 + 10, x2 = 100 + 10 + picWidth;
            int y1 = 30 + 10, y2 = 30 + 10 + picHeight;

            int shadowColor = 0xFF000000;
            int shadowOffset = 3;
            guiGraphics.fill(
                    (int) (x1 + shadowOffset), (int) (y1 + shadowOffset),
                    (int) (x2 + shadowOffset), (int) (y2 + shadowOffset),
                    shadowColor
            );
            if (!comment.image.url.isEmpty()) {
                RenderSystem.setShaderTexture(0, ImageDownload.getTexture(comment.image.url).getId());
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                Matrix4f matrix4f = guiGraphics.pose().last().pose();
                BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
                bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                bufferBuilder.vertex(matrix4f, x1, y1, 0).uv(0, 0).endVertex();
                bufferBuilder.vertex(matrix4f, x1, y2, 0).uv(0, 1).endVertex();
                bufferBuilder.vertex(matrix4f, x2, y2, 0).uv(1, 1).endVertex();
                bufferBuilder.vertex(matrix4f, x2, y1, 0).uv(1, 0).endVertex();
                BufferUploader.drawWithShader(bufferBuilder.end());
            } else {
                guiGraphics.fillGradient(x1, y1, x2, y2, 0xff014e7c, 0xff501639);
            }

            WidgetCommentEntry widget = getWidget(comment);
            widget.showImage = false;
            int imgAreaWidth = width - 100 - 20 - 10;
            widget.setBounds(100 + 10 + imgAreaWidth - (imgAreaWidth / 2), 0, imgAreaWidth / 2);
            widget.setBounds(100 + 10 + imgAreaWidth - (imgAreaWidth / 2), height - 20 - widget.getHeight(),
                    imgAreaWidth / 2);
            widget.render(guiGraphics, mouseX, mouseY, partialTick);

            boolean canDelete = minecraft.player.hasPermissions(3)
                    || minecraft.player.getGameProfile().getId().equals(comment.initiator);
            if (canDelete) {
                int deleteBtnX = 100 + 18, deleteBtnY = height - 20 - 22;
                guiGraphics.blit(ATLAS_LOCATION, deleteBtnX, deleteBtnY, 20, 20,
                        216, 60, 20, 20, 256, 256);
                if (mouseX > deleteBtnX && mouseX < deleteBtnX + 20
                        && mouseY > deleteBtnY && mouseY < deleteBtnY + 20) {
                    guiGraphics.blit(ATLAS_LOCATION, deleteBtnX, deleteBtnY, 20, 20,
                            236, 60, 20, 20, 256, 256);
                    if (commentToDelete == comment) {
                        guiGraphics.renderTooltip(font, Component.translatable("gui.worldcomment.list.remove.confirm"), mouseX, mouseY);
                    }
                }
            }
        } else {
            graphicsBlit9(guiGraphics, 100, 30, width - 120, height - 50,
                    176, 40, 20, 20, 256, 256,
                    4, 4, 4, 4
            );
            guiGraphics.enableScissor(102, 32, width - 22, height - 22);
            int yOffset = 32 + 4;
            for (int i = commentListOffset; i < commentList.size(); i++) {
                CommentEntry comment = commentList.get(i);
                WidgetCommentEntry widget = getWidget(comment);
                widget.showImage = true;
                widget.setBounds(106, yOffset, width - 102 - 22 - 8 - 4 - 16);
                widget.render(guiGraphics, mouseX, mouseY, partialTick);

                guiGraphics.blit(ATLAS_LOCATION, width - 22 - 4 - 16, yOffset + 4, 16, 16,
                        196, 60, 20, 20, 256, 256);
                if (mouseX > width - 22 - 4 - 16 && mouseX < width - 22 - 4
                        && mouseY > yOffset + 4 && mouseY < yOffset + 4 + 16) {
                    guiGraphics.blit(ATLAS_LOCATION, width - 22 - 4 - 16, yOffset + 4, 16, 16,
                            236, 60, 20, 20, 256, 256);
                    }

                boolean canDelete = minecraft.player.hasPermissions(3)
                        || minecraft.player.getGameProfile().getId().equals(comment.initiator);
                if (canDelete) {
                    yOffset += 16;
                    guiGraphics.blit(ATLAS_LOCATION, width - 22 - 4 - 16, yOffset + 4, 16, 16,
                            216, 60, 20, 20, 256, 256);
                    if (mouseX > width - 22 - 4 - 16 && mouseX < width - 22 - 4
                            && mouseY > yOffset + 4 && mouseY < yOffset + 4 + 16) {
                        guiGraphics.blit(ATLAS_LOCATION, width - 22 - 4 - 16, yOffset + 4, 16, 16,
                                236, 60, 20, 20, 256, 256);
                        if (commentToDelete == comment) {
                            guiGraphics.renderTooltip(font, Component.translatable("gui.worldcomment.list.remove.confirm"), mouseX, mouseY);
                        }
                    }
                    yOffset -= 16;
                }

                yOffset += widget.getHeight() + 6;
                if (yOffset > height - 22) break;
            }
            guiGraphics.disableScissor();

            if (commentList.size() > 1) {
                String pageStr = String.format("↕ %d / %d", commentListOffset + 1, commentList.size());
                guiGraphics.drawString(Minecraft.getInstance().font, pageStr,
                        width - 10 - 10 - Minecraft.getInstance().font.width(pageStr),
                        15, 0xFFA5D6A7, true);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (subScreen == 3) {
            CommentEntry comment = commentForDetail;
            boolean canDelete = minecraft.player.hasPermissions(3)
                    || minecraft.player.getGameProfile().getId().equals(comment.initiator);
            if (canDelete) {
                int deleteBtnX = 100 + 18, deleteBtnY = height - 20 - 22;
                if (mouseX > deleteBtnX && mouseX < deleteBtnX + 20
                        && mouseY > deleteBtnY && mouseY < deleteBtnY + 20) {
                    if (comment == commentToDelete) {
                        PacketEntryActionC2S.ClientLogics.send(comment, PacketEntryActionC2S.ACTION_DELETE);
                        commentList.remove(comment);
                        commentToDelete = null;
                        commentListOffset = Mth.clamp(commentListOffset, 0, Math.max(commentList.size() - 1, 0));
                        onClose();
                    } else {
                        commentToDelete = comment;
                    }
                }
            }
        } else {
            int yOffset = 32 + 4;
            for (int i = commentListOffset; i < commentList.size(); i++) {
                CommentEntry comment = commentList.get(i);
                WidgetCommentEntry widget = getWidget(comment);

                if (mouseX > width - 22 - 4 - 16 && mouseX < width - 22 - 4
                        && mouseY > yOffset + 4 && mouseY < yOffset + 4 + 16) {
                    this.commentForDetail = comment;
                    useSubScreen(3);
                    return true;
                }

                yOffset += 16;
                if (mouseX > width - 22 - 4 - 16 && mouseX < width - 22 - 4
                        && mouseY > yOffset + 4 && mouseY < yOffset + 4 + 16) {
                    if (comment == commentToDelete) {
                        PacketEntryActionC2S.ClientLogics.send(comment, PacketEntryActionC2S.ACTION_DELETE);
                        commentList.remove(comment);
                        commentToDelete = null;
                        commentListOffset = Mth.clamp(commentListOffset, 0, Math.max(commentList.size() - 1, 0));
                    } else {
                        commentToDelete = comment;
                    }
                    return true;
                }
                yOffset -= 16;

                yOffset += widget.getHeight() + 6;
                if (yOffset > height - 22) break;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        super.renderBackground(guiGraphics);
        graphicsBlit9(guiGraphics, 30, 10, width - 40, height - 20,
                196, 40, 20, 20, 256, 256,
                4, 4, 4, 4
        );
    }

    private double accumulatedScroll = 0;

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.accumulatedScroll != 0.0 && Math.signum(delta) != Math.signum(this.accumulatedScroll)) {
            this.accumulatedScroll = 0.0;
        }
        this.accumulatedScroll += delta;
        int scrollAmount = (int)this.accumulatedScroll;
        if (scrollAmount == 0) return super.mouseScrolled(mouseX, mouseY, delta);
        this.accumulatedScroll -= scrollAmount;

        if (commentList.size() <= 1) {
            commentListOffset = 0;
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int dir = -(int)Math.signum(scrollAmount);
        commentListOffset = Mth.clamp(commentListOffset + dir, 0, Math.max(commentList.size() - 1, 0));

        if (subScreen == 1 && commentListOffset >= latestCommentsRequestedAmount - latestCommentsPageSize / 2) {
            lastRequestNonce = ServerWorldData.SNOWFLAKE.nextId();
            PacketCollectionRequestC2S.ClientLogics.sendLatest(
                    latestCommentsRequestedAmount, latestCommentsPageSize, lastRequestNonce);
            latestCommentsRequestedAmount += latestCommentsPageSize;
        }
        return true;
    }

    @Override
    public void onClose() {
        if (subScreen == 3 && prevSubScreen != 3) {
            useSubScreen(prevSubScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private long lastRequestNonce;

    public void handleCommentDataUI(List<CommentEntry> data, long nonce) {
        if (nonce != lastRequestNonce) return;
        commentList.addAll(data);
        commentList.sort(Comparator.comparingLong(entry -> -entry.timestamp));
        commentListOffset = Mth.clamp(commentListOffset, 0, Math.max(commentList.size() - 1, 0));
    }

    public static boolean handleKeyF5() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;

        ItemStack item = minecraft.player.getMainHandItem();
        if (!item.is(Main.ITEM_COMMENT_TOOL.get())) return false;

        minecraft.execute(() -> {
            if (minecraft.screen instanceof CommentListScreen) {
                minecraft.screen.onClose();
            } else if (minecraft.screen == null) {
                minecraft.setScreen(new CommentListScreen(null));
            }
        });
        return true;
    }

    public static boolean handleKeyTab() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;

        if (ClientRayPicking.pickedComments.size() == 0) return false;

        minecraft.execute(() -> {
            if (minecraft.screen instanceof CommentListScreen) {
                minecraft.screen.onClose();
            } else if (minecraft.screen == null) {
                minecraft.setScreen(
                        new CommentListScreen(ClientRayPicking.pickedComments.get(ClientRayPicking.overlayOffset)));
            }
        });
        return true;
    }
}
