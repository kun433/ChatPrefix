void processShakeEffect(inout vec4 vertex, float speed, float intensity) {
    if (speed <= 0.0) speed = 1.0;
    if (intensity <= 0.0) intensity = 1.0;

    float charId = floor(float(gl_VertexID) / 4.0);
    float time = GameTime * 32000.0 * speed;
    
    float noiseX = noise(charId * 10.0 + time) - 0.5;
    float noiseY = noise(charId * 10.0 - time + 100.0) - 0.5;

    vertex.x += noiseX * intensity;
    vertex.y += noiseY * intensity;

    applyProjection(vertex);
    finalize();
}
