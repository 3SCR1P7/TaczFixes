package com.ssscript.taczfixes.client.render.light;

import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.nio.FloatBuffer;

/**
 * Uniform Buffer Object 包装。移植自 Shimmer (MIT)。
 */
public class ShaderUBO {
    public final int id;
    private boolean inValid;
    private int blockBinding = -1;

    public ShaderUBO() {
        id = GL30.glGenBuffers();
    }

    public void bindBuffer() {
        GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, id);
    }

    public void unBindBuffer() {
        GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
    }

    public void createBufferData(long size, int mode) {
        bindBuffer();
        GL30.glBufferData(GL31.GL_UNIFORM_BUFFER, size, mode);
        unBindBuffer();
    }

    public void bufferSubData(long offset, FloatBuffer data) {
        bindBuffer();
        GL30.glBufferSubData(GL31.GL_UNIFORM_BUFFER, offset, data);
        unBindBuffer();
    }

    public void bufferSubData(long offset, float[] data) {
        bindBuffer();
        GL30.glBufferSubData(GL31.GL_UNIFORM_BUFFER, offset, data);
        unBindBuffer();
    }

    public void bufferSubData(long offset, int[] data) {
        bindBuffer();
        GL30.glBufferSubData(GL31.GL_UNIFORM_BUFFER, offset, data);
        unBindBuffer();
    }

    public void blockBinding(int blockBinding) {
        this.blockBinding = blockBinding;
        if (blockBinding > -1) {
            GL31.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, blockBinding, id);
        }
    }

    public void bindToShader(int program, String bufBlockName) {
        if (blockBinding > -1) {
            GL31.glUniformBlockBinding(program, GL31.glGetUniformBlockIndex(program, bufBlockName), blockBinding);
        }
    }
}
