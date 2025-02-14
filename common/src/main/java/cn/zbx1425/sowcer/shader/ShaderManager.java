package cn.zbx1425.sowcer.shader;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.batch.ShaderProp;
import cn.zbx1425.sowcer.util.AttrUtil;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ShaderManager {

    public static final VertexFormatElement MC_ELEMENT_MATRIX =
            new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 16);

    public static final VertexFormat MC_FORMAT_BLOCK_MAT = new VertexFormat(ImmutableMap.<String, VertexFormatElement>builder()
            .put("Position", DefaultVertexFormat.ELEMENT_POSITION).put("Color", DefaultVertexFormat.ELEMENT_COLOR)
            .put("UV0", DefaultVertexFormat.ELEMENT_UV0).put("UV1", DefaultVertexFormat.ELEMENT_UV1).put("UV2", DefaultVertexFormat.ELEMENT_UV2)
            .put("Normal", DefaultVertexFormat.ELEMENT_NORMAL)
            .put("ModelMat", MC_ELEMENT_MATRIX)
            .put("Padding", DefaultVertexFormat.ELEMENT_PADDING)
            .build()
    );

    public final Map<String, ShaderInstance> shaders = new HashMap<>();

    public boolean isReady() {
        return this.shaders.size() > 0;
    }

    public void reloadShaders(ResourceManager resourceManager) throws IOException {
        this.shaders.values().forEach(ShaderInstance::close);
        this.shaders.clear();
        PatchingResourceProvider provider = new PatchingResourceProvider(resourceManager);

        loadShader(provider, "rendertype_entity_cutout");
        loadShader(provider, "rendertype_entity_translucent_cull");
        loadShader(provider, "rendertype_beacon_beam");
    }

    private void loadShader(ResourceProvider resourceManager, String name) throws IOException {
        ShaderInstance shader = new ShaderInstance(resourceManager, name, MC_FORMAT_BLOCK_MAT);
        shaders.put(name, shader);
    }

    public void setupShaderBatchState(MaterialProp materialProp, ShaderProp shaderProp) {
        RenderSystem.assertOnRenderThread();

        // ShaderState
        ShaderInstance shaderInstance = shaders.get(materialProp.shaderName);

        materialProp.setupCompositeState();

        for (int l = 0; l < 8; ++l) {
            int o = RenderSystem.getShaderTexture(l);
            shaderInstance.setSampler("Sampler" + l, o);
        }
        if (shaderInstance.MODEL_VIEW_MATRIX != null) {
            Matrix4f mvMatrix = RenderSystem.getModelViewMatrix().copy();
            if (shaderProp.viewMatrix != null) {
                mvMatrix.multiply(shaderProp.viewMatrix);
            }
            if (materialProp.billboard) AttrUtil.zeroRotation(mvMatrix);
            shaderInstance.MODEL_VIEW_MATRIX.set(mvMatrix);
        }
        if (shaderInstance.PROJECTION_MATRIX != null) {
            shaderInstance.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }
        if (shaderInstance.INVERSE_VIEW_ROTATION_MATRIX != null) {
            shaderInstance.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        }
        if (shaderInstance.COLOR_MODULATOR != null) {
            shaderInstance.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }
        if (shaderInstance.FOG_START != null) {
            shaderInstance.FOG_START.set(RenderSystem.getShaderFogStart());
        }
        if (shaderInstance.FOG_END != null) {
            shaderInstance.FOG_END.set(RenderSystem.getShaderFogEnd());
        }
        if (shaderInstance.FOG_COLOR != null) {
            shaderInstance.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }
        if (shaderInstance.FOG_SHAPE != null) {
            shaderInstance.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }
        if (shaderInstance.TEXTURE_MATRIX != null) {
            shaderInstance.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }
        if (shaderInstance.GAME_TIME != null) {
            shaderInstance.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }
        if (shaderInstance.SCREEN_SIZE != null) {
            Window window = Minecraft.getInstance().getWindow();
            shaderInstance.SCREEN_SIZE.set((float)window.getWidth(), (float)window.getHeight());
        }

        if (shaderProp.viewMatrix == null) {
            RenderSystem.setupShaderLights(shaderInstance);
        } else {
            Vector3f light0Dir, light1Dir;
            final Vector3f DIFFUSE_LIGHT_0 = Util.make(new Vector3f(0.2f, 1.0f, -0.7f), Vector3f::normalize);
            final Vector3f DIFFUSE_LIGHT_1 = Util.make(new Vector3f(-0.2f, 1.0f, 0.7f), Vector3f::normalize);
            final Vector3f NETHER_DIFFUSE_LIGHT_0 = Util.make(new Vector3f(0.2f, 1.0f, -0.7f), Vector3f::normalize);
            final Vector3f NETHER_DIFFUSE_LIGHT_1 = Util.make(new Vector3f(-0.2f, -1.0f, 0.7f), Vector3f::normalize);
            if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.effects().constantAmbientLight()) {
                light0Dir = NETHER_DIFFUSE_LIGHT_0;
                light1Dir = NETHER_DIFFUSE_LIGHT_1;
            } else {
                light0Dir = DIFFUSE_LIGHT_0;
                light1Dir = DIFFUSE_LIGHT_1;
            }
            if (shaderInstance.LIGHT0_DIRECTION != null) {
                shaderInstance.LIGHT0_DIRECTION.set(light0Dir);
            }
            if (shaderInstance.LIGHT1_DIRECTION != null) {
                shaderInstance.LIGHT1_DIRECTION.set(light1Dir);
            }
        }
        shaderInstance.apply();
    }

}
