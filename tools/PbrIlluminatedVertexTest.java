import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.client.render.pbr.IlluminatedVertexConsumer;
import java.lang.reflect.Proxy;

/** Exercises both packed and bulk vertex APIs used by Bedrock cubes. */
public class PbrIlluminatedVertexTest {
    public static void main(String[] args) {
        int[] light = {-1, -1};
        VertexConsumer sink = (VertexConsumer) Proxy.newProxyInstance(
                VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("uv2") && values.length == 2) {
                        light[0] = (int) values[0]; light[1] = (int) values[1];
                    }
                    return method.getReturnType() == VertexConsumer.class ? proxy : null;
                });
        VertexConsumer marked = new IlluminatedVertexConsumer(sink);
        marked.uv2(0x00F000F0);
        check(light);
        // An unmarked child inherits its parent's consumer; a marked child can wrap again.
        new IlluminatedVertexConsumer(marked).vertex(0, 0, 0, 1, 1, 1, 1,
                0, 0, 0, 0x00F000F0, 0, 0, 1);
        check(light);
        sink.uv2(240, 240);
        if ((light[0] & 1) != 0) throw new AssertionError("Marker leaked to ordinary group");
        System.out.println("PASS packed/bulk vertices retain the group marker, nested wrappers are idempotent, vanilla lightmap index unchanged");
    }

    private static void check(int[] light) {
        if (light[0] != 241 || light[1] != 240 || light[0] / 16 != 15)
            throw new AssertionError("Invalid illuminated vertex lightmap coordinates");
    }
}
