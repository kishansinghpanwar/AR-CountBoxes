uniform mat4 uViewProjection;

attribute vec4 vPosition;

void main() {
    gl_Position = uViewProjection * vPosition;
}