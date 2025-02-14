package cn.zbx1425.sowcerext.model;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.model.Model;
import cn.zbx1425.sowcer.util.AttrUtil;
import cn.zbx1425.sowcer.vertex.VertAttrMapping;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RawModel {

    public ResourceLocation sourceLocation;

    public HashMap<MaterialProp, RawMesh> meshList = new HashMap<>();

    public Model upload(VertAttrMapping mapping) {
        Model model = new Model();
        for (RawMesh mesh : meshList.values()) {
            if (mesh.faces.size() == 0) continue;
            if (!mesh.checkVertIndex()) throw new IndexOutOfBoundsException("RawModel contains invalid vertex index");
            model.meshList.add(mesh.upload(mapping));
        }
        return model;
    }

    public void append(RawMesh nextMesh) {
        if (meshList.containsKey(nextMesh.materialProp)) {
            RawMesh mesh = meshList.get(nextMesh.materialProp);
            mesh.append(nextMesh);
        } else {
            RawMesh newMesh = new RawMesh(nextMesh.materialProp);
            meshList.put(nextMesh.materialProp, newMesh);
            newMesh.append(nextMesh);
        }
    }

    public void append(Collection<RawMesh> nextMesh) {
        for (RawMesh mesh : nextMesh) append(mesh);
    }

    public void append(RawModel nextModel) {
        append(nextModel.meshList.values());
    }

    public void applyMatrix(Matrix4f matrix) {
        for (RawMesh mesh : meshList.values()) mesh.applyMatrix(matrix);
    }

    public void applyTranslation(float x, float y, float z) {
        for (RawMesh mesh : meshList.values()) mesh.applyTranslation(x, y, z);
    }

    public void applyRotation(Vector3f axis, float angle) {
        for (RawMesh mesh : meshList.values()) mesh.applyRotation(axis, angle);
    }

    public void applyScale(float x, float y, float z) {
        for (RawMesh mesh : meshList.values()) mesh.applyScale(x, y, z);
    }

    public void applyMirror(boolean vx, boolean vy, boolean vz, boolean nx, boolean ny, boolean nz) {
        for (RawMesh mesh : meshList.values()) mesh.applyMirror(vx, vy, vz, nx, ny, nz);
    }

    public void applyShear(Vector3f dir, Vector3f shear, float ratio) {
        for (RawMesh mesh : meshList.values()) mesh.applyShear(dir, shear, ratio);
    }

    public void writeBlazeBuffer(MultiBufferSource vertexConsumers, Matrix4f matrix, int light) {
        for (Map.Entry<MaterialProp, RawMesh> entry : meshList.entrySet()) {
            RenderType renderType = entry.getKey().getBlazeRenderType();
            int resultColor = entry.getKey().attrState.color != null ? entry.getKey().attrState.color : 0xFFFFFFFF;
            int resultLight = entry.getKey().attrState.lightmapUV != null ? entry.getKey().attrState.lightmapUV : light;

            if (Objects.equals(entry.getKey().shaderName, "rendertype_entity_translucent_cull") && (resultColor & 0xFF) != 0xFF) {
                // TEMP WORKAROUND: Depth sorting breaks
                continue;
            }

            Matrix4f resultMatrix = matrix;
            if (entry.getKey().billboard) {
                resultMatrix = matrix.copy();
                AttrUtil.zeroRotation(resultMatrix);
            }

            entry.getValue().writeBlazeBuffer(vertexConsumers.getBuffer(renderType), resultMatrix, resultColor, resultLight);
        }
    }

    public RawModel copy() {
        RawModel result = new RawModel();
        result.sourceLocation = this.sourceLocation;
        for (RawMesh mesh : this.meshList.values()) result.meshList.put(mesh.materialProp, mesh.copy());
        return result;
    }
}
