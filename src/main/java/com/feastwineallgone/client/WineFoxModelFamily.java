package com.feastwineallgone.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Central Wine Fox family identification.
 *
 * Important:
 * YSM maids can keep a generic TLM model id while their real selected model is
 * stored in getYsmModelId()/getYsmModelTexture()/getYsmModelName().
 * Therefore checking getModelId() alone is not sufficient.
 */
public final class WineFoxModelFamily {

    private WineFoxModelFamily() {
    }

    public static boolean isWineFox(
            EntityMaid maid
    ) {
        if (maid == null) {
            return false;
        }

        if (looksLikeWineFox(maid.getModelId())) {
            return true;
        }

        if (maid.isYsmModel()) {
            if (looksLikeWineFox(maid.getYsmModelId())) {
                return true;
            }

            if (looksLikeWineFox(maid.getYsmModelTexture())) {
                return true;
            }

            Component name =
                    maid.getYsmModelName();

            if (name != null
                    && looksLikeWineFox(name.getString())) {
                return true;
            }
        }

        return false;
    }

    public static boolean isWineFoxModelId(
            String modelId
    ) {
        return looksLikeWineFox(modelId);
    }

    private static boolean looksLikeWineFox(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return false;
        }

        String normalized =
                value.toLowerCase(Locale.ROOT);

        return normalized.contains("winefox")
                || normalized.contains("wine_fox")
                || normalized.contains("wine-fox")
                || normalized.contains("wine fox")
                || normalized.contains("酒狐");
    }
}
