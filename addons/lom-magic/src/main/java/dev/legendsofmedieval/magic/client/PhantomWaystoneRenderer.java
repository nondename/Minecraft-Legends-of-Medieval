package dev.legendsofmedieval.magic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.legendsofmedieval.magic.LoMMagic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Client-only spectral Waystone preview mesh: a translucent pedestal, shaft
 * and tapered crystal tip. It follows the temporary server-side anchor.
 * Final pixel-art Waystones-inspired textures can replace the vanilla texture.
 */
@Mod.EventBusSubscriber(modid = LoMMagic.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PhantomWaystoneRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/block/stone_bricks.png");

    private PhantomWaystoneRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        PoseStack stack = event.getPoseStack();
        var cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vertex = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand anchor) || anchor.getCustomName() == null) continue;
            String identifier = anchor.getCustomName().getString();
            if (!identifier.startsWith("LoMPhantomWaystone:")) continue;
            int tier;
            try { tier = Integer.parseInt(identifier.substring("LoMPhantomWaystone:".length())); }
            catch (NumberFormatException ignored) { continue; }
            if (tier < 1 || tier > 3) continue;
            stack.pushPose();
            stack.translate(anchor.getX() - cam.x, anchor.getY() - cam.y, anchor.getZ() - cam.z);
            float time = (mc.level.getGameTime() + event.getPartialTick()) / 20.0f;
            stack.translate(0, 0.045 * Math.sin(time * 2), 0);
            int r = 183;
            int g = 82;
            int b = 255;
            float alpha = 0.46f + 0.07f * (float) Math.sin(time * 3);
            cuboid(vertex, stack, -.49f, 0f, -.49f, .49f, .24f, .49f, r, g, b, alpha);
            cuboid(vertex, stack, -.37f, .24f, -.37f, .37f, .38f, .37f, r, g, b, alpha);
            cuboid(vertex, stack, -.27f, .38f, -.27f, .27f, 1.91f, .27f, r, g, b, alpha);
            cuboid(vertex, stack, -.32f, .66f, -.32f, .32f, .73f, .32f, r, g, b, alpha);
            cuboid(vertex, stack, -.32f, 1.42f, -.32f, .32f, 1.50f, .32f, r, g, b, alpha);
            cuboid(vertex, stack, -.34f, 1.83f, -.34f, .34f, 2.03f, .34f, r, g, b, alpha);
            tip(vertex, stack, .31f, 2.03f, 2.50f, r, g, b, alpha);
            stack.popPose();
        }
        buffers.endBatch(RenderType.entityTranslucent(TEXTURE));
    }

    private static void tip(VertexConsumer v, PoseStack s, float radius, float y0, float y1,
                            int r, int g, int b, float a) {
        for (int side = 0; side < 4; side++) {
            double t0 = Math.PI * (.25 + side * .5);
            double t1 = Math.PI * (.25 + (side + 1) * .5);
            float x0 = (float) Math.cos(t0) * radius, z0 = (float) Math.sin(t0) * radius;
            float x1 = (float) Math.cos(t1) * radius, z1 = (float) Math.sin(t1) * radius;
            face(v,s,x0,y0,z0,x1,y0,z1,0,y1,0,r,g,b,a);
        }
    }

    private static void cuboid(VertexConsumer v, PoseStack s,
                               float x0,float y0,float z0,float x1,float y1,float z1,
                               int r,int g,int b,float a) {
        face(v,s,x0,y0,z1,x1,y0,z1,x1,y1,z1,r,g,b,a);
        face(v,s,x1,y0,z0,x0,y0,z0,x0,y1,z0,r,g,b,a);
        face(v,s,x1,y0,z1,x1,y0,z0,x1,y1,z0,r,g,b,a);
        face(v,s,x0,y0,z0,x0,y0,z1,x0,y1,z1,r,g,b,a);
        face(v,s,x0,y1,z1,x1,y1,z1,x1,y1,z0,r,g,b,a);
    }

    private static void face(VertexConsumer v, PoseStack s, float x0,float y0,float z0,
                             float x1,float y1,float z1,float x2,float y2,float z2,
                             int r,int g,int b,float a) {
        // Triangular fan rendered as a quad; for rectangular faces the fourth
        // corner is inferred from the other three corners.
        boolean triangle = (x2 == 0 && z2 == 0 && y2 > y0 && x0 != x1 && z0 != z1);
        float x3=triangle ? x2 : x0+x2-x1;
        float y3=triangle ? y2 : y0+y2-y1;
        float z3=triangle ? z2 : z0+z2-z1;
        Matrix4f pos=s.last().pose();
        Matrix3f normal=s.last().normal();
        float alpha=Math.max(0f,Math.min(1f,a));
        add(v,pos,normal,x0,y0,z0,0,1,r,g,b,alpha);
        add(v,pos,normal,x1,y1,z1,1,1,r,g,b,alpha);
        add(v,pos,normal,x2,y2,z2,1,0,r,g,b,alpha);
        add(v,pos,normal,x3,y3,z3,0,0,r,g,b,alpha);
    }

    private static void add(VertexConsumer v, Matrix4f pos, Matrix3f normal,
                            float x,float y,float z,float u,float texV,
                            int r,int g,int b,float a) {
        v.vertex(pos,x,y,z).color(r,g,b,(int)(a*255)).uv(u,texV)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(normal,0,1,0).endVertex();
    }
}
