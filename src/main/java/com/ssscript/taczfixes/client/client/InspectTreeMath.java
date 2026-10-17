package com.ssscript.taczfixes.client.client;

import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 检视动画过滤用的节点树路径/层级计算(纯函数)。 */
final class InspectTreeMath {
    private InspectTreeMath() {
    }

    static List<BedrockPart> pathFromAncestor(BedrockPart ancestor, BedrockPart descendant, Map<BedrockPart, BedrockPart> parents) {
        if (ancestor == null || descendant == null || !isAncestor(ancestor, descendant, parents)) {
            return Collections.emptyList();
        }
        List<BedrockPart> reversed = new ArrayList<>();
        BedrockPart bedrockPart = descendant;
        while (true) {
            BedrockPart current = bedrockPart;
            if (current == null || current == ancestor) {
                break;
            }
            reversed.add(current);
            bedrockPart = parents.get(current);
        }
        reversed.add(ancestor);
        Collections.reverse(reversed);
        return reversed;
    }

    static BedrockPart lowestCommonAncestor(BedrockPart first, BedrockPart second, Map<BedrockPart, BedrockPart> parents) {
        if (first == null || second == null) {
            return null;
        }
        Set<BedrockPart> ancestors = Collections.newSetFromMap(new IdentityHashMap());
        BedrockPart bedrockPart = first;
        while (true) {
            BedrockPart current = bedrockPart;
            if (current == null) {
                break;
            }
            ancestors.add(current);
            bedrockPart = parents.get(current);
        }
        BedrockPart bedrockPart2 = second;
        while (true) {
            BedrockPart current2 = bedrockPart2;
            if (current2 != null) {
                if (!ancestors.contains(current2)) {
                    bedrockPart2 = parents.get(current2);
                } else {
                    return current2;
                }
            } else {
                return null;
            }
        }
    }

    static boolean isAncestor(BedrockPart ancestor, BedrockPart descendant, Map<BedrockPart, BedrockPart> parents) {
        if (ancestor == null || descendant == null) {
            return false;
        }
        BedrockPart bedrockPart = descendant;
        while (true) {
            BedrockPart current = bedrockPart;
            if (current != null) {
                if (current != ancestor) {
                    bedrockPart = parents.get(current);
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    static BedrockPart findNodeNormalized(Map<String, BedrockPart> nodesByName, String normalizedName) {
        for (Map.Entry<String, BedrockPart> entry : nodesByName.entrySet()) {
            if (InspectModelNames.normalize(entry.getKey()).equals(normalizedName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    static BedrockPart findHoldingHandEndpoint(Map<String, BedrockPart> nodes, Map<BedrockPart, BedrockPart> parents) {
        String[] candidates = {"righthandpos", "righthandposition", "handrightpos", "handrightposition", "rhandpos", "rhandposition", "mainhandpos", "mainhandposition", "holdinghandpos", "holdinghandposition", "rightarmpos", "rightarmposition"};
        for (String candidate : candidates) {
            BedrockPart found = findNodeNormalized(nodes, candidate);
            if (found != null) {
                return found;
            }
        }
        for (BedrockPart part : nodes.values()) {
            String name = InspectModelNames.normalize(part.name);
            if (!name.equals("pos") && !name.equals("position")) {
                continue;
            }
            BedrockPart current = parents.get(part);
            while (current != null) {
                if (isHoldingHandControlName(InspectModelNames.normalize(current.name))) {
                    return part;
                }
                current = parents.get(current);
            }
        }
        return null;
    }

    static boolean isHoldingHandControlName(String normalizedName) {
        return normalizedName.equals("righthand") || normalizedName.equals("handright") || normalizedName.equals("rhand") || normalizedName.equals("mainhand") || normalizedName.equals("holdinghand") || normalizedName.equals("rightarm");
    }

    static boolean isHoldingEndpointControlPath(BedrockPart part, BedrockPart holdingHandEndpoint, Map<BedrockPart, BedrockPart> parents) {
        if (part == null || holdingHandEndpoint == null) {
            return false;
        }
        BedrockPart holdingControl = null;
        BedrockPart bedrockPart = parents.get(holdingHandEndpoint);
        while (true) {
            BedrockPart current = bedrockPart;
            if (current == null) {
                break;
            }
            if (!isHoldingHandControlName(InspectModelNames.normalize(current.name))) {
                bedrockPart = parents.get(current);
            } else {
                holdingControl = current;
                break;
            }
        }
        if (holdingControl == null) {
            return part == holdingHandEndpoint;
        }
        return pathFromAncestor(holdingControl, holdingHandEndpoint, parents).contains(part);
    }

}
