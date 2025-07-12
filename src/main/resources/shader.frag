#version 330 core

out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoord;

uniform sampler2D texture_diffuse1; // Przykładowa tekstura

void main()
{
    FragColor = texture(texture_diffuse1, TexCoord);
}