import com.ssscript.taczfixes.client.render.pbr.CelestialLight;

public class PbrCelestialLightTest {
    static void near(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.00001f)
            throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        var noon = CelestialLight.sample(0, 0, 0, 1);
        var morning = CelestialLight.sample(0.875f, 0, 0, 1);
        var afternoon = CelestialLight.sample(0.125f, 0, 0, 1);
        var midnight = CelestialLight.sample(0.5f, 0, 0, 1);
        near(noon.x(), 0); near(noon.y(), 1);
        near(morning.x(), (float)Math.sqrt(0.5));
        near(afternoon.x(), -(float)Math.sqrt(0.5));
        near(midnight.x(), 0); near(midnight.y(), 1);
        if (midnight.red() >= noon.red() || midnight.blue() <= midnight.red())
            throw new AssertionError("Moonlight must be weaker and cooler");
        near(CelestialLight.sample(0, 1, 1, 1).red(), 0);
        near(CelestialLight.sample(0.5f, 0, 0, 0).blue(), 0);
        near(CelestialLight.sample(0.25f, 0, 0, 1).red(), 0);
        // Check direction at a non-cardinal time against the vanilla sky rotation.
        float t = 0.137f;
        var moving = CelestialLight.sample(t, 0, 0, 1);
        near(moving.x(), -(float)Math.sin(t * 2 * Math.PI));
        near(moving.y(), (float)Math.cos(t * 2 * Math.PI));
        System.out.println("PASS sun orbit, opposite moon orbit, horizon fade, rain and lunar intensity");
    }
}
