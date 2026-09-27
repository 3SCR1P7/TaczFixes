import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import java.lang.reflect.Field;
import static org.lwjgl.opengl.GL33C.*;

/** Exercises Minecraft's real BlendMode cache, not a simulated shader blend state. */
public class PbrBlendStateRegression {
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(16, 16, "PBR state regression", 0, 0);
        if (window == 0) throw new AssertionError("GL context failed");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            RenderSystem.initRenderThread();
            Field cache = BlendMode.class.getDeclaredField("lastApplied");
            cache.setAccessible(true);
            BlendMode vanilla = new BlendMode(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_FUNC_ADD);
            BlendMode opaque = new BlendMode();
            BlendMode additive = new BlendMode(GL_ONE, GL_ONE, GL_ZERO, GL_ONE, GL_FUNC_ADD);
            vanilla.apply();
            Object saved = cache.get(null);
            opaque.apply();
            additive.apply();
            RenderSystem.blendFuncSeparate(GL_ZERO, GL_ONE_MINUS_SRC_COLOR, GL_ONE, GL_ZERO);
            vanilla.apply();
            if (glGetInteger(GL_BLEND_SRC_RGB) != GL_SRC_ALPHA)
                throw new AssertionError("Did not reproduce old vignette blend corruption");
            System.out.println("REPRODUCED old bug: vanilla vignette factors overwritten after PBR composite");
            opaque.apply();
            additive.apply();
            cache.set(null, saved);
            RenderSystem.blendFuncSeparate(GL_ZERO, GL_ONE_MINUS_SRC_COLOR, GL_ONE, GL_ZERO);
            vanilla.apply();
            if (glGetInteger(GL_BLEND_SRC_RGB) != GL_ZERO || glGetInteger(GL_BLEND_DST_RGB) != GL_ONE_MINUS_SRC_COLOR)
                throw new AssertionError("Vignette factors corrupted after cache restoration");
            System.out.println("PASS restored cache preserves Minecraft vignette blending");
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
