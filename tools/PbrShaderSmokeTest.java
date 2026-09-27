import java.nio.*;
import java.nio.file.*;
import java.util.zip.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;

public class PbrShaderSmokeTest {
    static Path root;
    static ZipFile minecraft;
    static String source(String file) throws Exception {
        String s = Files.readString(root.resolve(file));
        for (String include : new String[]{"light", "fog"}) {
            var entry = minecraft.getEntry("assets/minecraft/shaders/include/" + include + ".glsl");
            try (var in = minecraft.getInputStream(entry)) {
                s = s.replace("#moj_import <" + include + ".glsl>", new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replaceAll("(?m)^#version.*$", ""));
            }
        }
        return s;
    }
    static int compile(int type, String file) throws Exception {
        int s = glCreateShader(type);
        glShaderSource(s, source(file)); glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE) throw new AssertionError(file + ": " + glGetShaderInfoLog(s));
        return s;
    }
    static int program(String vertex, String fragment) throws Exception {
        int p = glCreateProgram();
        int v = compile(GL_VERTEX_SHADER, vertex), f = compile(GL_FRAGMENT_SHADER, fragment);
        glAttachShader(p, v); glAttachShader(p, f); glLinkProgram(p);
        if (glGetProgrami(p, GL_LINK_STATUS) == GL_FALSE) throw new AssertionError(fragment + ": " + glGetProgramInfoLog(p));
        glDeleteShader(v); glDeleteShader(f);
        System.out.println("PASS compile/link " + fragment);
        return p;
    }
    static int texture(int unit, float r, float g, float b, float a) {
        glActiveTexture(GL_TEXTURE0 + unit);
        int id = glGenTextures(); glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 1, 1, 0, GL_RGBA, GL_FLOAT, new float[]{r,g,b,a});
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return id;
    }
    static void set(int p, String name, float value) {glUniform1f(glGetUniformLocation(p,name),value);}
    static void seti(int p, String name, int value) {glUniform1i(glGetUniformLocation(p,name),value);}
    static void attr(int p, String name, float... values) {
        int l=glGetAttribLocation(p,name);if(l<0)return;
        if(values.length==2)glVertexAttrib2f(l,values[0],values[1]);
        if(values.length==3)glVertexAttrib3f(l,values[0],values[1],values[2]);
        if(values.length==4)glVertexAttrib4f(l,values[0],values[1],values[2],values[3]);
    }
    static float pixel(int p, float alpha, float sceneDepth) {
        texture(3,0.8f,1,0,alpha);texture(5,sceneDepth,0,0,1);
        glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
        glDrawArrays(GL_TRIANGLES,0,3);
        FloatBuffer pixel=BufferUtils.createFloatBuffer(4);
        glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
        return pixel.get(0);
    }
    static float readPixel(int x, int y) {
        FloatBuffer result=BufferUtils.createFloatBuffer(4);
        glReadPixels(x,y,1,1,GL_RGBA,GL_FLOAT,result);
        return result.get(0);
    }
    static void screen(int p) {
        glUseProgram(p);
        int vao=glGenVertexArrays();glBindVertexArray(vao);
        int b=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,b);
        glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,0,0,0,3,-1,0,2,0,-1,3,0,0,2},GL_STATIC_DRAW);
        int pos=glGetAttribLocation(p,"Position"),uv=glGetAttribLocation(p,"UV0");
        glEnableVertexAttribArray(pos);glVertexAttribPointer(pos,3,GL_FLOAT,false,20,0);
        glEnableVertexAttribArray(uv);glVertexAttribPointer(uv,2,GL_FLOAT,false,20,12);
        for(int i=0;i<3;i++)seti(p,"Sampler"+i,i);
        glViewport(0,0,9,9);
    }
    static void postTest(int blur, int composite) {
        screen(blur);
        texture(0,0,0,0,1);
        float[] pixels=new float[9*9*4];
        pixels[(4*9+4)*4]=1;
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,9,9,0,GL_RGBA,GL_FLOAT,pixels);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        texture(1,0.5f,0,0,1);texture(2,1,0,0,1);
        seti(blur,"CheckDepth",1);glUniform2f(glGetUniformLocation(blur,"Direction"),4f/9f,0);
        glClear(GL_COLOR_BUFFER_BIT);glDrawArrays(GL_TRIANGLES,0,3);
        float center=readPixel(4,4),halo=readPixel(5,4),outer=readPixel(6,4);
        if(center<=halo||halo<=outer||outer<=0.01f)
            throw new AssertionError("discontinuous blur: "+center+", "+halo+", "+outer);
        texture(2,0.25f,0,0,1);
        glClear(GL_COLOR_BUFFER_BIT);glDrawArrays(GL_TRIANGLES,0,3);
        if(readPixel(4,4)>0.01f||readPixel(5,4)>0.01f)throw new AssertionError("occluded bloom");
        System.out.println("PASS blur spreads emission; later scene occluders suppress the halo source");
        screen(composite);texture(0,0.4f,0.4f,0.4f,1);set(composite,"Strength",0.5f);
        glClearColor(0.2f,0.2f,0.2f,1);glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_BLEND);glBlendFuncSeparate(GL_ONE,GL_ONE,GL_ZERO,GL_ONE);
        glDrawArrays(GL_TRIANGLES,0,3);
        if(Math.abs(readPixel(4,4)-0.52f)>0.02f)throw new AssertionError("additive composite");
        glDisable(GL_BLEND);
        System.out.println("PASS additive bloom composite preserves the scene");
    }
    static void celestialTest(int p, int uv2) {
        set(p,"CelestialVisibility",1);set(p,"LocalSkyExposure",1);
        seti(p,"HasNormal",0);seti(p,"EmissionPass",0);set(p,"SkyAmbient",0);
        texture(0,0.8f,0.8f,0.8f,1);
        texture(2,1,1,1,1);
        float[] lightmap=new float[16*16*4];java.util.Arrays.fill(lightmap,1f);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,16,16,0,GL_RGBA,GL_FLOAT,lightmap);
        glVertexAttribI2i(uv2,0,240);
        glUniform3f(glGetUniformLocation(p,"CelestialColor"),1,1,1);
        glUniform3f(glGetUniformLocation(p,"CelestialDirection"),0,0,1);
        float aligned=pixel(p,1,1);
        set(p,"CelestialVisibility",0);
        float blocked=pixel(p,1,1);
        if(aligned<blocked+0.2f)throw new AssertionError("Ceiling/wall occlusion did not suppress highlight");
        set(p,"CelestialVisibility",1);
        glUniform3f(glGetUniformLocation(p,"CelestialDirection"),1,0,0);
        float sideways=pixel(p,1,1);
        if(aligned<sideways+0.2f)throw new AssertionError("Directional highlight: "+aligned+", "+sideways);
        // Rotate the camera: the same world-space light must align with the view normal again.
        glUniformMatrix3fv(glGetUniformLocation(p,"IViewRotMat"),false,new float[]{0,0,-1,0,1,0,1,0,0});
        float rotated=pixel(p,1,1);
        if(Math.abs(rotated-aligned)>0.02f)throw new AssertionError("Camera-space light direction");
        glUniform3f(glGetUniformLocation(p,"CelestialColor"),0,0,0);
        float disabled=pixel(p,1,1);
        glUniform3f(glGetUniformLocation(p,"CelestialColor"),1,1,1);
        glVertexAttribI2i(uv2,0,0);
        float underground=pixel(p,1,1);
        if(disabled>sideways+0.02f||underground>sideways+0.02f)
            throw new AssertionError("Absent celestial light or skylight must not create a highlight");
        System.out.println("PASS moving celestial highlight, camera rotation, no-sky and underground suppression");
    }
    public static void main(String[] args) throws Exception {
        root=Path.of(args[0]);minecraft=new ZipFile(args[1]);
        if(!GLFW.glfwInit())throw new AssertionError("GLFW init");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(16,16,"PBR shader validation",0,0);
        if(window==0)throw new AssertionError("OpenGL context");
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            System.out.println(glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION));
            int p=program("gun_pbr.vsh","gun_pbr.fsh");
            int blur=program("pbr_screen.vsh","pbr_blur.fsh");
            int composite=program("pbr_screen.vsh","pbr_composite.fsh");
            int vao=glGenVertexArrays();glBindVertexArray(vao);
            int buffer=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,buffer);
            glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,-0.5f,3,-1,-0.5f,-1,3,-0.5f},GL_STATIC_DRAW);
            int pos=glGetAttribLocation(p,"Position");glEnableVertexAttribArray(pos);glVertexAttribPointer(pos,3,GL_FLOAT,false,0,0);
            glUseProgram(p);
            float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
            glUniformMatrix4fv(glGetUniformLocation(p,"ModelViewMat"),false,identity);
            glUniformMatrix4fv(glGetUniformLocation(p,"ProjMat"),false,identity);
            glUniformMatrix3fv(glGetUniformLocation(p,"IViewRotMat"),false,new float[]{1,0,0,0,1,0,0,0,1});
            glUniform4f(glGetUniformLocation(p,"ColorModulator"),1,1,1,1);
            attr(p,"Color",1,1,1,1);attr(p,"Normal",0,0,1);attr(p,"UV0",0,0);
            int uv1=glGetAttribLocation(p,"UV1"),uv2=glGetAttribLocation(p,"UV2");
            glVertexAttribI2i(uv1,0,0);glVertexAttribI2i(uv2,0,0);
            for(int i=0;i<6;i++)seti(p,"Sampler"+i,i);
            texture(0,1,0.5f,0.2f,1);texture(1,0,0,0,1);texture(2,1,1,1,1);texture(4,0.5f,0.5f,1,1);
            set(p,"FogStart",100);set(p,"FogEnd",200);set(p,"EmissionStrength",1);set(p,"ReflectionStrength",1);
            seti(p,"EmissionPass",1);seti(p,"HasNormal",0);seti(p,"HasSpecular",1);
            glViewport(0,0,1,1);glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);
            float dark=pixel(p,1,1),bright=pixel(p,254f/255f,1),half=pixel(p,127f/255f,1),hidden=pixel(p,254f/255f,0);
            if(dark>0.01f||bright<0.95f||Math.abs(half-0.5f)>0.02f||hidden>0.01f)
                throw new AssertionError("emission pixels: "+dark+", "+bright+", "+half+", "+hidden);
            System.out.println("PASS LabPBR A=255 dark, A=254 bright, A=127 half, depth occlusion");
            seti(p,"HasSpecular",0);glVertexAttribI2i(uv2,241,240);
            float groupGlow=pixel(p,1,1);
            glVertexAttribI2i(uv2,240,240);
            float ordinary=pixel(p,1,1);
            if(groupGlow<0.95f||ordinary>0.01f)throw new AssertionError("illuminated group: "+groupGlow+", "+ordinary);
            System.out.println("PASS illuminated group emits without a specular texture");
            glVertexAttribI2i(uv2,0,0);seti(p,"HasSpecular",1);
            seti(p,"EmissionPass",0);seti(p,"HasNormal",1);
            float normal=pixel(p,1,1);
            if(!Float.isFinite(normal))throw new AssertionError("non-finite normal shading");
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("OpenGL error");
            System.out.println("PASS normal mapping and material pass");
            celestialTest(p,uv2);
            postTest(blur,composite);
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("OpenGL post error");
        }finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();minecraft.close();}
    }
}
