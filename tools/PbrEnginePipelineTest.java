import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.ssscript.taczfixes.client.render.pbr.IlluminatedVertexConsumer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.lwjgl.opengl.GL33C.*;

/** Real Minecraft buffer upload, shader JSON, sampler binding, targets and post-processing. */
public class PbrEnginePipelineTest {
    static boolean thin;
    static final Matrix4f IDENTITY = new Matrix4f();
    static int tex(float r, float g, float b) {
        int t=glGenTextures();RenderSystem.bindTexture(t);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_FLOAT,new float[]{r,g,b,1});
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        return t;
    }
    static VertexBuffer quad(boolean entity) {
        BufferBuilder b=new BufferBuilder(512);
        b.begin(VertexFormat.Mode.QUADS,entity?DefaultVertexFormat.NEW_ENTITY:DefaultVertexFormat.POSITION_TEX);
        VertexConsumer v=entity?new IlluminatedVertexConsumer(b):b;
        float s=entity?0.25f:1f;
        float low=entity&&thin?0f:-s, high=entity&&thin?1f/16f:s;
        for(float[] xy:new float[][]{{-s,low},{s,low},{s,high},{-s,high}}) {
            if(entity)v.vertex(xy[0],xy[1],0,1,1,1,1,0,0,0,0xF000F0,0,0,1);
            else v.vertex(xy[0],xy[1],0).uv((xy[0]+1)/2,(xy[1]+1)/2).endVertex();
        }
        VertexBuffer result=new VertexBuffer(VertexBuffer.Usage.STATIC);
        result.bind();result.upload(b.end());return result;
    }
    static float pixel(int x,int y) {float[] p=new float[4];glReadPixels(x,y,1,1,GL_RGBA,GL_FLOAT,p);return p[0];}
    static void draw(VertexBuffer mesh,ShaderInstance shader) {mesh.bind();mesh.drawWithShader(IDENTITY,IDENTITY,shader);}
    public static void main(String[] args) throws Exception {
        thin=args.length>2 && args[2].equals("thin");
        GLFW.glfwInit();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long w=GLFW.glfwCreateWindow(32,32,"PBR engine pipeline",0,0);
        GLFW.glfwMakeContextCurrent(w);GL.createCapabilities();RenderSystem.initRenderThread();
        var config=net.minecraftforge.common.ForgeConfig.class.getDeclaredField("clientSpec");config.setAccessible(true);
        ((net.minecraftforge.common.ForgeConfigSpec)config.get(null)).setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
        try(ZipFile mc=new ZipFile(args[1])) {
            var pack=(net.minecraft.server.packs.PackResources)java.lang.reflect.Proxy.newProxyInstance(
                    PbrEnginePipelineTest.class.getClassLoader(),new Class[]{net.minecraft.server.packs.PackResources.class},
                    (proxy,method,values)->method.getName().equals("packId")?"pbr-test":null);
            ResourceProvider resources=id -> Optional.of(new Resource(pack, () -> {
                if(id.getNamespace().equals("taczfixes")) {
                    String source=Files.readString(Path.of(args[0],id.getPath()));
                    // Resolve vanilla includes without bootstrapping Forge's unrelated registries.
                    for(String include:new String[]{"light","fog"}) {
                        try(var in=mc.getInputStream(mc.getEntry("assets/minecraft/shaders/include/"+include+".glsl"))) {
                            source=source.replace("#moj_import <"+include+".glsl>",new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replaceAll("(?m)^#version.*$",""));
                        }
                    }
                    return new java.io.ByteArrayInputStream(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                return mc.getInputStream(mc.getEntry("assets/"+id.getNamespace()+"/"+id.getPath()));
            }));
            var gun=new ShaderInstance(resources,new ResourceLocation("taczfixes","gun_pbr"),DefaultVertexFormat.NEW_ENTITY);
            var blur=new ShaderInstance(resources,new ResourceLocation("taczfixes","pbr_blur"),DefaultVertexFormat.POSITION_TEX);
            var composite=new ShaderInstance(resources,new ResourceLocation("taczfixes","pbr_composite"),DefaultVertexFormat.POSITION_TEX);
            var main=new TextureTarget(32,32,true,false);
            main.enableStencil();
            var emission=new TextureTarget(32,32,true,false);
            emission.enableStencil();
            emission.setFilterMode(GL_LINEAR);
            var horizontal=new TextureTarget(32,32,false,false);
            var vertical=new TextureTarget(32,32,false,false);
            horizontal.setFilterMode(GL_LINEAR);vertical.setFilterMode(GL_LINEAR);
            var mesh=quad(true);var screen=quad(false);
            RenderSystem.setShaderTexture(0,tex(1,0.5f,0.2f));
            RenderSystem.setShaderTexture(1,tex(0,0,0));RenderSystem.setShaderTexture(2,tex(1,1,1));
            RenderSystem.setShaderTexture(3,tex(0,0,0));RenderSystem.setShaderTexture(4,tex(0.5f,0.5f,1));
            RenderSystem.setShaderFogStart(100);RenderSystem.setShaderFogEnd(200);
            RenderSystem.setShaderLights(new org.joml.Vector3f(0,1,0),new org.joml.Vector3f(0,1,0));
            gun.safeGetUniform("EmissionStrength").set(1f);
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableCull();
            main.setClearColor(0,0,0,0);main.clear(false);main.bindWrite(true);
            draw(mesh,gun);
            emission.setClearColor(0,0,0,0);emission.clear(false);emission.bindWrite(true);
            RenderSystem.setShaderTexture(5,main.getDepthTextureId());gun.safeGetUniform("EmissionPass").set(1);
            draw(mesh,gun);
            float source=pixel(16,16);System.out.println((thin?"Thin":"Large")+" emission source="+source);
            if(source<0.5f)throw new AssertionError("Lost illuminated group during real engine capture");
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();
            horizontal.bindWrite(true);RenderSystem.setShaderTexture(0,emission.getColorTextureId());
            RenderSystem.setShaderTexture(1,emission.getDepthTextureId());RenderSystem.setShaderTexture(2,main.getDepthTextureId());
            blur.safeGetUniform("CheckDepth").set(1);blur.safeGetUniform("Direction").set(6f/32,0f);draw(screen,blur);
            System.out.println("Horizontal="+pixel(16,16));
            vertical.bindWrite(true);RenderSystem.setShaderTexture(0,horizontal.getColorTextureId());
            blur.safeGetUniform("CheckDepth").set(0);blur.safeGetUniform("Direction").set(0f,6f/32);draw(screen,blur);
            main.bindWrite(true);float before=pixel(21,16);
            RenderSystem.setShaderTexture(0,vertical.getColorTextureId());composite.safeGetUniform("Strength").set(0.6f);
            // Exercise state enforcement after shader apply, including a stale blend setup.
            RenderSystem.blendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);
            screen.bind();composite.setSampler("Sampler0",vertical.getColorTextureId());composite.apply();
            RenderSystem.disableDepthTest();RenderSystem.disableCull();RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GL_ONE,GL_ONE,GL_ZERO,GL_ONE);
            screen.draw();composite.clear();
            float halo=pixel(21,16)-before;System.out.println("Halo="+halo);
            if(halo<0.01f)throw new AssertionError("No halo through Minecraft's real post-processing path");
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("OpenGL error");
            System.out.println("PASS real engine illuminated vertex -> capture -> blur -> scene halo");
        } finally {GLFW.glfwDestroyWindow(w);GLFW.glfwTerminate();}
    }
}
