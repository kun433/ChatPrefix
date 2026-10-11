void processScaleEffect(inout vec4 vertex, float expansion, float offsetX, float offsetY) {
    float vertexId = mod(float(gl_VertexID), 4.0);
    vec2 dir = vec2(
        (vertexId == 2.0 || vertexId == 3.0) ? 1.0 : -1.0,
        (vertexId == 1.0 || vertexId == 2.0) ? 1.0 : -1.0
    );

    float actualExpansion = (expansion - 1.0) * 4.0;
    dir *= vec2(0.7, 1.0);
    vec2 finalOffset = dir * actualExpansion + vec2(offsetX, offsetY);

    setOffset(finalOffset.x, finalOffset.y);
    applyOffset(vertex);

    applyProjection(vertex);
    finalize();
}