package com.ssscript.taczfixes.client.client;

import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public final class OffhandArmPoseResolver {
    private static final Map<BedrockAnimatedModel, BedrockPart> CARRIER_CACHE = Collections.synchronizedMap(new IdentityHashMap());

    private OffhandArmPoseResolver() {
    }

    public static Matrix4f resolve(BedrockAnimatedModel model, Matrix4f modelBase, Matrix4f rightArmPose) {
        if (model == null || modelBase == null || rightArmPose == null) {
            return rightArmPose;
        }
        BedrockPart carrier = resolveCarrier(model);
        if (carrier == null) {
            return rightArmPose;
        }
        Matrix4f carrierLocal = computeWorldMatrix(carrier);
        Matrix4f carrierPose = new Matrix4f(modelBase).mul(carrierLocal);
        float determinant = carrierPose.determinant();
        if (!isFinite(carrierPose) || !Float.isFinite(determinant) || Math.abs(determinant) < 1.0E-8f) {
            return rightArmPose;
        }
        Matrix4f relativePose = new Matrix4f(carrierPose).invert().mul(rightArmPose);
        if (!isFinite(relativePose)) {
            return rightArmPose;
        }
        if (OffhandDisplayManager.shouldUseInwardHoldingArmForManualAction(model)) {
            return rightArmPose;
        }
        Matrix4f result = new Matrix4f(carrierPose).mul(relativePose);
        return isFinite(result) ? result : rightArmPose;
    }

    public static void clear() {
        CARRIER_CACHE.clear();
    }

    public static Set<String> resolveHoldingArmAnimationNodes(BedrockAnimatedModel model) {
        if (model == null || model.getRootNode() == null) {
            return Collections.emptySet();
        }
        BedrockPart modelRoot = model.getRootNode();
        BedrockPart endpoint = findHoldingEndpoint(modelRoot);
        if (endpoint == null) {
            endpoint = findHoldingControlNode(modelRoot);
            if (endpoint == null) {
                return Collections.emptySet();
            }
        }
        BedrockPart carrier = lowestCommonAncestor(endpoint, findGunAnchor(model));
        if (carrier == null) {
            carrier = modelRoot;
        }
        if (endpoint == carrier) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        BedrockPart parent = endpoint;
        while (true) {
            BedrockPart current = parent;
            if (current != null && current != carrier) {
                String normalized = normalize(current.name);
                if (current != endpoint && isSharedOrMechanismNode(normalized)) {
                    break;
                }
                result.add(normalized);
                parent = current.getParent();
            } else {
                break;
            }
        }
        result.remove("");
        return result;
    }

    public static Set<String> resolveSupportArmAnimationNodes(BedrockAnimatedModel model) {
        if (model == null || model.getRootNode() == null) {
            return Collections.emptySet();
        }
        BedrockPart modelRoot = model.getRootNode();
        BedrockPart endpoint = findSupportEndpoint(modelRoot);
        if (endpoint == null) {
            endpoint = findSupportControlNode(modelRoot);
            if (endpoint == null) {
                return Collections.emptySet();
            }
        }
        BedrockPart carrier = lowestCommonAncestor(endpoint, findGunAnchor(model));
        if (carrier == null) {
            carrier = modelRoot;
        }
        if (endpoint == carrier) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        BedrockPart parent = endpoint;
        while (true) {
            BedrockPart current = parent;
            if (current != null && current != carrier) {
                String normalized = normalize(current.name);
                if (current != endpoint && isSharedOrMechanismNode(normalized)) {
                    break;
                }
                result.add(normalized);
                parent = current.getParent();
            } else {
                break;
            }
        }
        result.remove("");
        return result;
    }

    public static BedrockPart findNodeByNormalizedName(BedrockAnimatedModel model, String normalizedName) {
        if (model == null || normalizedName == null || normalizedName.isEmpty()) {
            return null;
        }
        Map<String, ?> bones = model.getIndexBones();
        if (bones != null) {
            for (String rawName : bones.keySet()) {
                if (normalizedName.equals(normalize(rawName))) {
                    BedrockPart part = model.getNode(rawName);
                    if (part != null) {
                        return part;
                    }
                }
            }
        }
        return model.getNode(normalizedName);
    }

    private static BedrockPart resolveCarrier(BedrockAnimatedModel model) {
        if (CARRIER_CACHE.containsKey(model)) {
            return CARRIER_CACHE.get(model);
        }
        BedrockPart holdingEndpoint = findHoldingEndpoint(model.getRootNode());
        BedrockPart holdingControl = findHoldingControl(holdingEndpoint);
        BedrockPart gunAnchor = findGunAnchor(model);
        BedrockPart carrier = lowestCommonAncestor(holdingEndpoint, gunAnchor);
        if (carrier == null || carrier == holdingEndpoint || carrier == holdingControl) {
            BedrockPart controlParent = holdingControl == null ? null : holdingControl.getParent();
            carrier = controlParent == null ? model.getRootNode() : controlParent;
        }
        CARRIER_CACHE.put(model, carrier);
        return carrier;
    }

    private static BedrockPart findGunAnchor(BedrockAnimatedModel model) {
        if (model instanceof BedrockGunModel) {
            BedrockGunModel gunModel = (BedrockGunModel) model;
            if (gunModel.getMuzzleFlashPosPath() != null && !gunModel.getMuzzleFlashPosPath().isEmpty()) {
                return (BedrockPart) gunModel.getMuzzleFlashPosPath().get(gunModel.getMuzzleFlashPosPath().size() - 1);
            }
        }
        if (model.getConstraintPath() != null && !model.getConstraintPath().isEmpty()) {
            return (BedrockPart) model.getConstraintPath().get(model.getConstraintPath().size() - 1);
        }
        return model.getRootNode();
    }

    public static BedrockPart findHoldingEndpoint(BedrockPart part) {
        if (part == null) {
            return null;
        }
        String normalized = normalize(part.name);
        if (isHoldingEndpointName(normalized)) {
            return part;
        }
        ObjectListIterator it = part.children.iterator();
        while (it.hasNext()) {
            BedrockPart child = (BedrockPart) it.next();
            BedrockPart result = findHoldingEndpoint(child);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public static BedrockPart findSupportEndpoint(BedrockPart part) {
        if (part == null) {
            return null;
        }
        String normalized = normalize(part.name);
        if (isSupportEndpointName(normalized)) {
            return part;
        }
        ObjectListIterator it = part.children.iterator();
        while (it.hasNext()) {
            BedrockPart child = (BedrockPart) it.next();
            BedrockPart result = findSupportEndpoint(child);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static boolean isHoldingEndpointName(String normalized) {
        String base = stripNumericSuffix(normalized);
        return base.equals("righthandpos") || base.equals("righthandposition") || base.equals("handrightpos") || base.equals("handrightposition") || base.equals("handrpos") || base.equals("handrposition") || base.equals("rhandpos") || base.equals("rhandposition") || base.equals("mainhandpos") || base.equals("mainhandposition") || base.equals("holdinghandpos") || base.equals("holdinghandposition") || base.equals("weaponhandpos") || base.equals("weaponhandposition") || base.equals("griphandpos") || base.equals("griphandposition") || base.equals("rightarmpos") || base.equals("rightarmposition") || base.equals("rightpos") || base.equals("rightposition") || base.equals("rpos") || base.equals("rposition");
    }

    private static boolean isSupportEndpointName(String normalized) {
        String base = stripNumericSuffix(normalized);
        return base.equals("lefthandpos") || base.equals("lefthandposition") || base.equals("handleftpos") || base.equals("handleftposition") || base.equals("handlpos") || base.equals("handlposition") || base.equals("lhandpos") || base.equals("lhandposition") || base.equals("offhandpos") || base.equals("offhandposition") || base.equals("supporthandpos") || base.equals("supporthandposition") || base.equals("leftarmpos") || base.equals("leftarmposition") || base.equals("leftpos") || base.equals("leftposition") || base.equals("lpos") || base.equals("lposition");
    }

    private static String stripNumericSuffix(String value) {
        int end = value.length();
        while (end > 0 && Character.isDigit(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static boolean isSharedOrMechanismNode(String normalized) {
        // 弹匣与手臂骨骼合并的节点(如 mag_and_lefthand): 只隐藏手臂, 不能连弹匣一起隐藏
        if (normalized.contains("mag") && (normalized.contains("hand") || normalized.contains("arm"))) {
            return true;
        }
        return normalized.contains("bolt") || normalized.contains("pump") || normalized.contains("slide") || normalized.contains("lever") || normalized.contains("shell") || normalized.contains("ammo") || normalized.contains("bullet") || normalized.contains("magazine") || normalized.contains("magzine") || normalized.equals("mag") || normalized.contains("muzzle") || normalized.contains("barrel") || normalized.contains("camera") || normalized.contains("constraint") || normalized.contains("weapon") || normalized.contains("gun") || normalized.equals("root");
    }

    private static BedrockPart findHoldingControl(BedrockPart endpoint) {
        BedrockPart current;
        BedrockPart parent = endpoint == null ? null : endpoint.getParent();
        while (true) {
            current = parent;
            if (current != null) {
                String normalized = normalize(current.name);
                if (normalized.equals("righthand") || normalized.equals("handright") || normalized.equals("rhand") || normalized.equals("mainhand") || normalized.equals("holdinghand") || normalized.equals("rightarm")) {
                    break;
                }
                parent = current.getParent();
            } else {
                return endpoint;
            }
        }
        return current;
    }

    private static BedrockPart findHoldingControlNode(BedrockPart part) {
        if (part == null) {
            return null;
        }
        String normalized = normalize(part.name);
        if (normalized.equals("righthand") || normalized.equals("handright") || normalized.equals("rhand") || normalized.equals("mainhand") || normalized.equals("holdinghand") || normalized.equals("rightarm")) {
            return part;
        }
        ObjectListIterator it = part.children.iterator();
        while (it.hasNext()) {
            BedrockPart child = (BedrockPart) it.next();
            BedrockPart result = findHoldingControlNode(child);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static BedrockPart findSupportControlNode(BedrockPart part) {
        if (part == null) {
            return null;
        }
        String normalized = normalize(part.name);
        if (normalized.equals("lefthand") || normalized.equals("handleft") || normalized.equals("lhand") || normalized.equals("offhand") || normalized.equals("supporthand") || normalized.equals("leftarm") || normalized.equals("left")) {
            return part;
        }
        ObjectListIterator it = part.children.iterator();
        while (it.hasNext()) {
            BedrockPart child = (BedrockPart) it.next();
            BedrockPart result = findSupportControlNode(child);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static BedrockPart lowestCommonAncestor(BedrockPart first, BedrockPart second) {
        if (first == null || second == null) {
            return null;
        }
        Set<BedrockPart> firstAncestors = Collections.newSetFromMap(new IdentityHashMap());
        BedrockPart parent = first;
        while (true) {
            BedrockPart current = parent;
            if (current == null) {
                break;
            }
            firstAncestors.add(current);
            parent = current.getParent();
        }
        BedrockPart parent2 = second;
        while (true) {
            BedrockPart current2 = parent2;
            if (current2 != null) {
                if (!firstAncestors.contains(current2)) {
                    parent2 = current2.getParent();
                } else {
                    return current2;
                }
            } else {
                return null;
            }
        }
    }

    private static Matrix4f computeWorldMatrix(BedrockPart part) {
        Matrix4f result = new Matrix4f();
        applyAncestors(part, result);
        return result;
    }

    private static void applyAncestors(BedrockPart part, Matrix4f target) {
        if (part == null) {
            return;
        }
        applyAncestors(part.getParent(), target);
        target.translate(part.offsetX, part.offsetY, part.offsetZ);
        target.translate(part.x / 16.0f, part.y / 16.0f, part.z / 16.0f);
        target.rotateZ(part.zRot);
        target.rotateY(part.yRot);
        target.rotateX(part.xRot);
        target.rotate(part.additionalQuaternion);
        target.scale(part.xScale, part.yScale, part.zScale);
    }

    private static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                result.append(Character.toLowerCase(character));
            }
        }
        return result.toString();
    }

    private static boolean isFinite(Matrix4f matrix) {
        return Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01()) && Float.isFinite(matrix.m02()) && Float.isFinite(matrix.m03()) && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11()) && Float.isFinite(matrix.m12()) && Float.isFinite(matrix.m13()) && Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21()) && Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23()) && Float.isFinite(matrix.m30()) && Float.isFinite(matrix.m31()) && Float.isFinite(matrix.m32()) && Float.isFinite(matrix.m33());
    }

}
