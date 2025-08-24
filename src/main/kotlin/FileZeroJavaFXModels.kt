package org.lewapnoob.FileZero2

import javafx.scene.paint.Color

fun createCubeMesh(size: Double, color: Color, inverted: Boolean = false): Mesh {
    val hs = size / 2.0
    val vertices = listOf(
        Vector3d(-hs, -hs, hs), Vector3d(hs, -hs, hs),
        Vector3d(hs, hs, hs), Vector3d(-hs, hs, hs),
        Vector3d(-hs, -hs, -hs), Vector3d(hs, -hs, -hs),
        Vector3d(hs, hs, -hs), Vector3d(-hs, hs, -hs)
    )
    var faces = listOf(
        listOf(0, 1, 2, 3),
        listOf(5, 4, 7, 6),
        listOf(3, 2, 6, 7),
        listOf(0, 4, 5, 1),
        listOf(1, 5, 6, 2),
        listOf(4, 0, 3, 7)

    )
    if (inverted) {
        faces = faces.map { it.reversed() }
    }
    val uvs: List<List<Vector3d>> = faces.map { face ->
        when(face.size) {
            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))
            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))
            else -> face.map { Vector3d(0.0,0.0,0.0) }
        }
    }
    return Mesh(vertices, faces, uvs, color)
}

fun createPyramidMesh(size: Double, color: Color): Mesh {
    val hs = size / 100.0
    val vertices = listOf(
        Vector3d(-50.0 * hs, 0.0 * hs, -50.0 * hs),
        Vector3d(50.0 * hs, 0.0 * hs, -50.0 * hs),
        Vector3d(-50.0 * hs, 0.0 * hs, 50.0 * hs),
        Vector3d(50.0 * hs, 0.0 * hs, 50.0 * hs),
        Vector3d(0.0 * hs, 100.0 * hs, 0.0 * hs),
        Vector3d(0.0 * hs, 100.0 * hs, 0.0 * hs),
    )
    val faces: List<List<Int>> = listOf(
        listOf(1, 3, 2, 0),
        listOf(1, 0, 4, 5),
        listOf(0, 2, 4, 5),
        listOf(2, 3, 4, 5),
        listOf(3, 1, 4, 5),
    )
    data class Edge(val a: Int, val b: Int)
    val edges: List<List<Edge>> = listOf(
        listOf(Edge(a=1, b=0)),
        listOf(Edge(a=0, b=2)),
        listOf(Edge(a=2, b=3)),
        listOf(Edge(a=3, b=1)),
        listOf(Edge(a=1, b=5)),
        listOf(Edge(a=5, b=0)),
        listOf(Edge(a=5, b=2)),
        listOf(Edge(a=5, b=3)),
    )
    val uvs: List<List<Vector3d>> = faces.map { face ->
        when(face.size) {
            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))
            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))
            else -> face.map { Vector3d(0.0,0.0,0.0) }
        }
    }
    val textureMapping = mapOf(
        0 to "floor",
        1 to "bricks",
        2 to "bricks",
        3 to "bricks",
        4 to "bricks"
    )
    return Mesh(vertices, faces, uvs, color, faceTextureNames = textureMapping)
}

fun createInvertedPyramidMesh(size: Double, color: Color): Mesh {
    val hs = size / 100.0
    val vertices = listOf(
        Vector3d(0.0 * hs, 0.0 * hs, 0.0 * hs),
        Vector3d(0.0 * hs, 0.0 * hs, 0.0 * hs),
        Vector3d(50.0 * hs, 100.0 * hs, -50.0 * hs),
        Vector3d(-50.0 * hs, 100.0 * hs, -50.0 * hs),
        Vector3d(-50.0 * hs, 100.0 * hs, 50.0 * hs),
        Vector3d(50.0 * hs, 100.0 * hs, 50.0 * hs),
    )
    val faces: List<List<Int>> = listOf(
        listOf(5, 2, 3, 4),
        listOf(3, 2, 1, 0),
        listOf(4, 3, 1, 0),
        listOf(5, 4, 1, 0),
        listOf(2, 5, 1, 0),
    )
    data class Edge(val a: Int, val b: Int)
    val edges: List<List<Edge>> = listOf(
        listOf(Edge(a=4, b=3)),
        listOf(Edge(a=3, b=2)),
        listOf(Edge(a=2, b=5)),
        listOf(Edge(a=5, b=4)),
        listOf(Edge(a=5, b=0)),
        listOf(Edge(a=0, b=4)),
        listOf(Edge(a=3, b=0)),
        listOf(Edge(a=0, b=2)),
    )
    val uvs: List<List<Vector3d>> = faces.map { face ->
        when(face.size) {
            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))
            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))
            else -> face.map { Vector3d(0.0,0.0,0.0) }
        }
    }
    val textureMapping = mapOf(
        0 to "floor",
        1 to "bricks",
        2 to "bricks",
        3 to "bricks",
        4 to "bricks"
    )
    return Mesh(vertices, faces, uvs, color, faceTextureNames = textureMapping)
}

fun createTowerMesh(size: Double, color: Color): Mesh {
    val hs = size / 100.0
    val vertices = listOf(
        Vector3d(-50.0 * hs, 0.0 * hs, -50.0 * hs),
        Vector3d(-50.0 * hs, 0.0 * hs, 50.0 * hs),
        Vector3d(50.0 * hs, 0.0 * hs, -50.0 * hs),
        Vector3d(-50.0 * hs, 50.0 * hs, -50.0 * hs),
        Vector3d(50.0 * hs, 0.0 * hs, 50.0 * hs),
        Vector3d(50.0 * hs, 50.0 * hs, -50.0 * hs),
        Vector3d(-50.0 * hs, 50.0 * hs, 50.0 * hs),
        Vector3d(50.0 * hs, 50.0 * hs, 50.0 * hs),
        Vector3d(-25.0 * hs, 50.0 * hs, -25.0 * hs),
        Vector3d(25.0 * hs, 50.0 * hs, -25.0 * hs),
        Vector3d(25.0 * hs, 50.0 * hs, 25.0 * hs),
        Vector3d(-25.0 * hs, 50.0 * hs, 25.0 * hs),
        Vector3d(-25.0 * hs, 100.0 * hs, -25.0 * hs),
        Vector3d(25.0 * hs, 100.0 * hs, -25.0 * hs),
        Vector3d(-25.0 * hs, 100.0 * hs, 25.0 * hs),
        Vector3d(25.0 * hs, 100.0 * hs, 25.0 * hs),
    )
    val faces: List<List<Int>> = listOf(
        listOf(7, 5, 3, 6),
        listOf(2, 0, 3, 5),
        listOf(0, 1, 6, 3),
        listOf(1, 4, 7, 6),
        listOf(4, 2, 5, 7),
        listOf(4, 1, 0, 2),
        listOf(8, 11, 14, 12),
        listOf(11, 10, 15, 14),
        listOf(10, 9, 13, 15),
        listOf(9, 8, 12, 13),
        listOf(15, 13, 12, 14),
    )
    val uvs: List<List<Vector3d>> = faces.map { face ->
        when(face.size) {
            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))
            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))
            else -> face.map { Vector3d(0.0,0.0,0.0) }
        }
    }
    return Mesh(vertices, faces, uvs, color)
}

fun createKotlinModelMesh(size: Double, color: Color): Mesh {
    val hs = size / 100.0
    val vertices = listOf(
        Vector3d(-50.0 * hs, 0.0 * hs, -50.0 * hs),
        Vector3d(50.0 * hs, 0.0 * hs, -50.0 * hs),
        Vector3d(0.0 * hs, 20.0 * hs, 0.0 * hs),
        Vector3d(0.0 * hs, 0.0 * hs, 0.0 * hs),
        Vector3d(50.0 * hs, 0.0 * hs, 0.0 * hs),
        Vector3d(-50.0 * hs, 0.0 * hs, 0.0 * hs),
        Vector3d(-50.0 * hs, 0.0 * hs, 50.0 * hs),
        Vector3d(50.0 * hs, 0.0 * hs, 50.0 * hs),
    )
    val faces: List<List<Int>> = listOf(
        listOf(2, 0, 5, 6),
        listOf(2, 6, 7),
        listOf(2, 7, 4, 1),
        listOf(2, 1, 3),
        listOf(2, 3, 0),
        listOf(6, 5, 4, 7),
        listOf(5, 0, 3),
        listOf(3, 1, 4),
    )
    val uvs: List<List<Vector3d>> = faces.map { face ->
        when(face.size) {
            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))
            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))
            else -> face.map { Vector3d(0.0,0.0,0.0) }
        }
    }
    return Mesh(vertices, faces, uvs, color)
}

fun createTankMesh(size: Double, color: Color): Mesh {
    val hs = size / 100.0
    val vertices = listOf(
        Vector3d(-30.0 * hs, 0.0 * hs, -40.0 * hs),
        Vector3d(30.0 * hs, 0.0 * hs, -40.0 * hs),
        Vector3d(30.0 * hs, 0.0 * hs, 40.0 * hs),
        Vector3d(-30.0 * hs, 0.0 * hs, 40.0 * hs),
        Vector3d(-40.0 * hs, 20.0 * hs, -60.0 * hs),
        Vector3d(-40.0 * hs, 20.0 * hs, 50.0 * hs),
        Vector3d(40.0 * hs, 20.0 * hs, 50.0 * hs),
        Vector3d(40.0 * hs, 20.0 * hs, -60.0 * hs),
        Vector3d(-20.0 * hs, 40.0 * hs, -20.0 * hs),
        Vector3d(20.0 * hs, 40.0 * hs, -20.0 * hs),
        Vector3d(20.0 * hs, 40.0 * hs, 40.0 * hs),
        Vector3d(-20.0 * hs, 40.0 * hs, 40.0 * hs),
        Vector3d(20.0 * hs, 60.0 * hs, 0.0 * hs),
        Vector3d(-20.0 * hs, 60.0 * hs, 0.0 * hs),
        Vector3d(-10.0 * hs, 40.0 * hs, -20.0 * hs),
        Vector3d(10.0 * hs, 40.0 * hs, -20.0 * hs),
        Vector3d(-10.0 * hs, 50.0 * hs, -10.0 * hs),
        Vector3d(10.0 * hs, 50.0 * hs, -10.0 * hs),
        Vector3d(-10.0 * hs, 50.0 * hs, -100.0 * hs),
        Vector3d(10.0 * hs, 50.0 * hs, -100.0 * hs),
        Vector3d(-10.0 * hs, 40.0 * hs, -100.0 * hs),
        Vector3d(10.0 * hs, 40.0 * hs, -100.0 * hs),
    )
    val faces: List<List<Int>> = listOf(
        listOf(4, 0, 3, 5),
        listOf(5, 3, 2, 6),
        listOf(6, 2, 1, 7),
        listOf(7, 1, 0, 4),
        listOf(1, 2, 3, 0),
        listOf(10, 6, 7, 9),
        listOf(11, 5, 6, 10),
        listOf(8, 4, 5, 11),
        listOf(9, 7, 4, 8),
        listOf(19, 21, 20, 18),
        listOf(13, 8, 11),
        listOf(13, 11, 10, 12),
        listOf(12, 10, 9),
        listOf(12, 9, 8, 13),
        listOf(18, 20, 14, 16),
        listOf(17, 19, 18, 16),
        listOf(17, 15, 21, 19),
        listOf(21, 15, 14, 20),
    )
    data class Edge(val a: Int, val b: Int)
    val edges: List<Edge> = listOf(
        Edge(a=2, b=3),
        Edge(a=3, b=0),
        Edge(a=0, b=1),
        Edge(a=1, b=2),
        Edge(a=4, b=5),
        Edge(a=4, b=7),
        Edge(a=6, b=7),
        Edge(a=6, b=5),
        Edge(a=5, b=3),
        Edge(a=6, b=2),
        Edge(a=7, b=1),
        Edge(a=4, b=0),
        Edge(a=19, b=18),
        Edge(a=18, b=20),
        Edge(a=20, b=21),
        Edge(a=21, b=19),
        Edge(a=17, b=16),
        Edge(a=16, b=14),
        Edge(a=14, b=15),
        Edge(a=15, b=17),
        Edge(a=16, b=18),
        Edge(a=20, b=14),
        Edge(a=15, b=21),
        Edge(a=19, b=17),
        Edge(a=8, b=9),
        Edge(a=9, b=10),
        Edge(a=10, b=11),
        Edge(a=11, b=8),
        Edge(a=8, b=4),
        Edge(a=11, b=5),
        Edge(a=10, b=6),
        Edge(a=7, b=9),
        Edge(a=12, b=13),
        Edge(a=12, b=10),
        Edge(a=9, b=12),
        Edge(a=8, b=13),
        Edge(a=13, b=11),
    )
    val uvs: List<List<Vector3d>> = faces.map { face ->
        when(face.size) {
            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))
            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))
            else -> face.map { Vector3d(0.0,0.0,0.0) }
        }
    }
    val textureMapping = mapOf(
        0 to "darkGreen",
        1 to "floor",
        2 to "darkGreen",
        3 to "ceiling",
        4 to "floor",

        5 to "green",
        6 to "floor",
        7 to "green",

        8 to "ceiling",

        9 to "blackBricks",
        10 to "ceiling",
        11 to "ceiling",
        12 to "ceiling",
        13 to "ceiling",
        14 to "blackBricks",
        15 to "blackBricks",
        16 to "blackBricks",
        17 to "blackBricks",
    )

    return Mesh(vertices, faces, uvs, color, faceTextureNames = textureMapping)
}

fun createOffroadCarMesh(size: Double, color: Color): Mesh {
    val hs = size / 100.0
    val vertices = listOf(
        Vector3d(20.0 * hs, 10.0 * hs, -40.0 * hs),
        Vector3d(-20.0 * hs, 10.0 * hs, -40.0 * hs),
        Vector3d(-20.0 * hs, 10.0 * hs, 40.0 * hs),
        Vector3d(20.0 * hs, 10.0 * hs, 40.0 * hs),
        Vector3d(20.0 * hs, 30.0 * hs, -40.0 * hs),
        Vector3d(-20.0 * hs, 30.0 * hs, -40.0 * hs),
        Vector3d(-20.0 * hs, 30.0 * hs, 25.0 * hs),
        Vector3d(20.0 * hs, 30.0 * hs, 25.0 * hs),
        Vector3d(18.0 * hs, 30.0 * hs, -30.0 * hs),
        Vector3d(-18.0 * hs, 30.0 * hs, -30.0 * hs),
        Vector3d(-18.0 * hs, 45.0 * hs, -20.0 * hs),
        Vector3d(18.0 * hs, 45.0 * hs, -20.0 * hs),
        Vector3d(20.0 * hs, 30.0 * hs, 22.0 * hs),
        Vector3d(-20.0 * hs, 30.0 * hs, 22.0 * hs),
        Vector3d(-20.0 * hs, 50.0 * hs, 22.0 * hs),
        Vector3d(20.0 * hs, 50.0 * hs, 22.0 * hs),
        Vector3d(29.0 * hs, 30.0 * hs, -25.0 * hs), Vector3d(29.0 * hs, 25.6 * hs, -35.6 * hs), Vector3d(29.0 * hs, 15.0 * hs, -40.0 * hs), Vector3d(29.0 * hs, 4.4 * hs, -35.6 * hs), Vector3d(29.0 * hs, 0.0 * hs, -25.0 * hs), Vector3d(29.0  * hs, 4.4 * hs, -14.4 * hs), Vector3d(29.0 * hs, 15.0 * hs, -10.0 * hs), Vector3d(29.0 * hs, 25.6 * hs, -14.4 * hs),
        Vector3d(21.0 * hs, 30.0 * hs, -25.0 * hs), Vector3d(21.0 * hs, 25.6 * hs, -35.6 * hs), Vector3d(21.0 * hs, 15.0 * hs, -40.0 * hs), Vector3d(21.0 * hs, 4.4 * hs, -35.6 * hs), Vector3d(21.0 * hs, 0.0 * hs, -25.0 * hs), Vector3d(21.0 * hs, 4.4 * hs, -14.4 * hs), Vector3d(21.0 * hs, 15.0 * hs, -10.0 * hs), Vector3d(21.0 * hs, 25.6 * hs, -14.4 * hs),
        Vector3d(-29.0 * hs, 30.0 * hs, -25.0 * hs), Vector3d(-29.0 * hs, 25.6 * hs, -35.6 * hs), Vector3d(-29.0 * hs, 15.0 * hs, -40.0 * hs), Vector3d(-29.0 * hs, 4.4 * hs, -35.6 * hs), Vector3d(-29.0 * hs, 0.0 * hs, -25.0 * hs), Vector3d(-29.0 * hs, 4.4 * hs, -14.4 * hs), Vector3d(-29.0 * hs, 15.0 * hs, -10.0 * hs), Vector3d(-29.0 * hs, 25.6 * hs, -14.4 * hs),
        Vector3d(-21.0 * hs, 30.0 * hs, -25.0 * hs), Vector3d(-21.0 * hs, 25.6 * hs, -35.6 * hs), Vector3d(-21.0 * hs, 15.0 * hs, -40.0 * hs), Vector3d(-21.0 * hs, 4.4 * hs, -35.6 * hs), Vector3d(-21.0 * hs, 0.0 * hs, -25.0 * hs), Vector3d(-21.0 * hs, 4.4 * hs, -14.4 * hs), Vector3d(-21.0 * hs, 15.0 * hs, -10.0 * hs), Vector3d(-21.0 * hs, 25.6 * hs, -14.4 * hs),
        Vector3d(29.0 * hs, 30.0 * hs, 25.0 * hs), Vector3d(29.0 * hs, 25.6 * hs, 14.4 * hs), Vector3d(29.0 * hs, 15.0 * hs, 10.0 * hs), Vector3d(29.0 * hs, 4.4 * hs, 14.4 * hs), Vector3d(29.0 * hs, 0.0 * hs, 25.0 * hs), Vector3d(29.0 * hs, 4.4 * hs, 35.6 * hs), Vector3d(29.0 * hs, 15.0 * hs, 40.0 * hs), Vector3d(29.0 * hs, 25.6 * hs, 35.6 * hs),
        Vector3d(21.0 * hs, 30.0 * hs, 25.0 * hs), Vector3d(21.0 * hs, 25.6 * hs, 14.4 * hs), Vector3d(21.0 * hs, 15.0 * hs, 10.0 * hs), Vector3d(21.0 * hs, 4.4 * hs, 14.4 * hs), Vector3d(21.0 * hs, 0.0 * hs, 25.0 * hs), Vector3d(21.0 * hs, 4.4 * hs, 35.6 * hs), Vector3d(21.0 * hs, 15.0 * hs, 40.0 * hs), Vector3d(21.0 * hs, 25.6 * hs, 35.6 * hs),
        Vector3d(-29.0 * hs, 30.0 * hs, 25.0 * hs), Vector3d(-29.0 * hs, 25.6 * hs, 14.4 * hs), Vector3d(-29.0 * hs, 15.0 * hs, 10.0 * hs), Vector3d(-29.0 * hs, 4.4 * hs, 14.4 * hs), Vector3d(-29.0 * hs, 0.0 * hs, 25.0 * hs), Vector3d(-29.0 * hs, 4.4 * hs, 35.6 * hs), Vector3d(-29.0 * hs, 15.0 * hs, 40.0 * hs), Vector3d(-29.0 * hs, 25.6 * hs, 35.6 * hs),
        Vector3d(-21.0 * hs, 30.0 * hs, 25.0 * hs), Vector3d(-21.0 * hs, 25.6 * hs, 14.4 * hs), Vector3d(-21.0 * hs, 15.0 * hs, 10.0 * hs), Vector3d(-21.0 * hs, 4.4 * hs, 14.4 * hs), Vector3d(-21.0 * hs, 0.0 * hs, 25.0 * hs), Vector3d(-21.0 * hs, 4.4 * hs, 35.6 * hs), Vector3d(-21.0 * hs, 15.0 * hs, 40.0 * hs), Vector3d(-21.0 * hs, 25.6 * hs, 35.6 * hs)
    )

    val singleSidedFaces: List<List<Int>> = listOf(
        listOf(0, 1, 5, 4), listOf(1, 2, 6, 5), listOf(2, 3, 7, 6),
        listOf(3, 0, 4, 7), listOf(4, 5, 6, 7), listOf(3, 2, 1, 0),
        listOf(8, 9, 10, 11), listOf(12, 13, 14, 15),
        listOf(16, 17, 18), listOf(16, 18, 19), listOf(16, 19, 20),
        listOf(16, 20, 21), listOf(16, 21, 22), listOf(16, 22, 23),
        listOf(31, 30, 29), listOf(31, 29, 28), listOf(31, 28, 27),
        listOf(31, 27, 26), listOf(31, 26, 25), listOf(31, 25, 24),
        listOf(16, 17, 25, 24), listOf(17, 18, 26, 25), listOf(18, 19, 27, 26), listOf(19, 20, 28, 27),
        listOf(20, 21, 29, 28), listOf(21, 22, 30, 29), listOf(22, 23, 31, 30), listOf(23, 16, 24, 31),
        listOf(39, 38, 37), listOf(39, 37, 36), listOf(39, 36, 35),
        listOf(39, 35, 34), listOf(39, 34, 33), listOf(39, 33, 32),
        listOf(40, 41, 42), listOf(40, 42, 43), listOf(40, 43, 44),
        listOf(40, 44, 45), listOf(40, 45, 46), listOf(40, 46, 47),
        listOf(33, 32, 40, 41), listOf(34, 33, 41, 42), listOf(35, 34, 42, 43), listOf(36, 35, 43, 44),
        listOf(37, 36, 44, 45), listOf(38, 37, 45, 46), listOf(39, 38, 46, 47), listOf(32, 39, 47, 40),
        listOf(48, 49, 50), listOf(48, 50, 51), listOf(48, 51, 52),
        listOf(48, 52, 53), listOf(48, 53, 54), listOf(48, 54, 55),
        listOf(63, 62, 61), listOf(63, 61, 60), listOf(63, 60, 59),
        listOf(63, 59, 58), listOf(63, 58, 57), listOf(63, 57, 56),
        listOf(48, 49, 57, 56), listOf(49, 50, 58, 57), listOf(50, 51, 59, 58), listOf(51, 52, 60, 59),
        listOf(52, 53, 61, 60), listOf(53, 54, 62, 61), listOf(54, 55, 63, 62), listOf(55, 48, 56, 63),
        listOf(71, 70, 69), listOf(71, 69, 68), listOf(71, 68, 67),
        listOf(71, 67, 66), listOf(71, 66, 65), listOf(71, 65, 64),
        listOf(72, 73, 74), listOf(72, 74, 75), listOf(72, 75, 76),
        listOf(72, 76, 77), listOf(72, 77, 78), listOf(72, 78, 79),
        listOf(65, 64, 72, 73), listOf(66, 65, 73, 74), listOf(67, 66, 74, 75), listOf(68, 67, 75, 76),
        listOf(69, 68, 76, 77), listOf(70, 69, 77, 78), listOf(71, 70, 78, 79), listOf(64, 71, 79, 72)
    )

    val faces: List<List<Int>> = singleSidedFaces.flatMap { originalFace ->
        listOf(originalFace, originalFace.reversed())
    }

    val uvs: List<List<Vector3d>> = faces.map { face ->
        when (face.size) {
            3 -> listOf(Vector3d(0.0, 0.0, 0.0), Vector3d(1.0, 0.0, 0.0), Vector3d(0.5, 1.0, 0.0))
            4 -> listOf(Vector3d(0.0, 1.0, 0.0), Vector3d(1.0, 1.0, 0.0), Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 0.0, 0.0))
            else -> face.map { Vector3d(0.0, 0.0, 0.0) }
        }
    }

    return Mesh(vertices, faces, uvs, color)
}

fun createStairMesh(size: Double, color: Color): Mesh {
    val hs = size / 100.0
    val vertices = listOf(
        Vector3d(-50.0 * hs, 0.0 * hs, -50.0 * hs),
        Vector3d(50.0 * hs, 0.0 * hs, -50.0 * hs),
        Vector3d(50.0 * hs, 0.0 * hs, 50.0 * hs),
        Vector3d(-50.0 * hs, 0.0 * hs, 50.0 * hs),
        Vector3d(50.0 * hs, 100.0 * hs, 50.0 * hs),
        Vector3d(-50.0 * hs, 100.0 * hs, 50.0 * hs),
        Vector3d(50.0 * hs, 50.0 * hs, -50.0 * hs),
        Vector3d(-50.0 * hs, 50.0 * hs, -50.0 * hs),
        Vector3d(-50.0 * hs, 0.0 * hs, 0.0 * hs),
        Vector3d(50.0 * hs, 0.0 * hs, 0.0 * hs),
        Vector3d(-50.0 * hs, 50.0 * hs, 0.0 * hs),
        Vector3d(50.0 * hs, 50.0 * hs, 0.0 * hs),
        Vector3d(-50.0 * hs, 100.0 * hs, 0.0 * hs),
        Vector3d(50.0 * hs, 100.0 * hs, 0.0 * hs),
    )
    val faces: List<List<Int>> = listOf(
        listOf(3, 2, 4, 5),
        listOf(0, 1, 2, 3),
        listOf(4, 2, 9, 13),
        listOf(11, 9, 1, 6),
        listOf(7, 0, 8, 10),
        listOf(12, 8, 3, 5),
        listOf(13, 11, 10, 12),
        listOf(11, 6, 7, 10),
        listOf(6, 1, 0, 7),
        listOf(4, 13, 12, 5),
    )
    val textureMapping = mapOf(
        1 to "floor",

        0 to "teal",
        2 to "teal",
        3 to "teal",
        4 to "teal",
        5 to "teal",
        6 to "teal",
        7 to "teal",
        8 to "teal",
        9 to "teal",
    )
    val uvs: List<List<Vector3d>> = faces.map { face ->
        when(face.size) {
            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))
            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))
            else -> face.map { Vector3d(0.0,0.0,0.0) }
        }
    }
    return Mesh(vertices, faces, uvs, color, faceTextureNames = textureMapping)
}

fun createCapsuleMesh(size: Double, color: Color): Mesh {
    val hs = size / 100.0
    val vertices = listOf(
        Vector3d(0.0 * hs, 225.0 * hs, 0.0 * hs),
        Vector3d(19.13417161825449 * hs, 221.19397662556435 * hs, 0.0 * hs),
        Vector3d(17.67766952966369 * hs, 221.19397662556435 * hs, 7.322330470336313 * hs),
        Vector3d(13.529902503654927 * hs, 221.19397662556435 * hs, 13.529902503654926 * hs),
        Vector3d(7.322330470336314 * hs, 221.19397662556435 * hs, 17.67766952966369 * hs),
        Vector3d(1.1716301013315748E-15 * hs, 221.19397662556435 * hs, 19.13417161825449 * hs),
        Vector3d(-7.322330470336312 * hs, 221.19397662556435 * hs, 17.67766952966369 * hs),
        Vector3d(-13.529902503654926 * hs, 221.19397662556435 * hs, 13.529902503654927 * hs),
        Vector3d(-17.67766952966369 * hs, 221.19397662556435 * hs, 7.322330470336316 * hs),
        Vector3d(-19.13417161825449 * hs, 221.19397662556435 * hs, 2.3432602026631496E-15 * hs),
        Vector3d(-17.677669529663692 * hs, 221.19397662556435 * hs, -7.322330470336311 * hs),
        Vector3d(-13.52990250365493 * hs, 221.19397662556435 * hs, -13.529902503654926 * hs),
        Vector3d(-7.322330470336324 * hs, 221.19397662556435 * hs, -17.677669529663685 * hs),
        Vector3d(-3.514890303994724E-15 * hs, 221.19397662556435 * hs, -19.13417161825449 * hs),
        Vector3d(7.3223304703363175 * hs, 221.19397662556435 * hs, -17.67766952966369 * hs),
        Vector3d(13.529902503654924 * hs, 221.19397662556435 * hs, -13.52990250365493 * hs),
        Vector3d(17.677669529663685 * hs, 221.19397662556435 * hs, -7.322330470336325 * hs),
        Vector3d(35.35533905932738 * hs, 210.35533905932738 * hs, 0.0 * hs),
        Vector3d(32.664074121909415 * hs, 210.35533905932738 * hs, 13.529902503654926 * hs),
        Vector3d(25.000000000000004 * hs, 210.35533905932738 * hs, 25.0 * hs),
        Vector3d(13.529902503654927 * hs, 210.35533905932738 * hs, 32.664074121909415 * hs),
        Vector3d(2.164890140588733E-15 * hs, 210.35533905932738 * hs, 35.35533905932738 * hs),
        Vector3d(-13.529902503654924 * hs, 210.35533905932738 * hs, 32.664074121909415 * hs),
        Vector3d(-25.0 * hs, 210.35533905932738 * hs, 25.000000000000004 * hs),
        Vector3d(-32.664074121909415 * hs, 210.35533905932738 * hs, 13.52990250365493 * hs),
        Vector3d(-35.35533905932738 * hs, 210.35533905932738 * hs, 4.329780281177466E-15 * hs),
        Vector3d(-32.664074121909415 * hs, 210.35533905932738 * hs, -13.529902503654922 * hs),
        Vector3d(-25.000000000000007 * hs, 210.35533905932738 * hs, -25.0 * hs),
        Vector3d(-13.529902503654945 * hs, 210.35533905932738 * hs, -32.66407412190941 * hs),
        Vector3d(-6.4946704217662E-15 * hs, 210.35533905932738 * hs, -35.35533905932738 * hs),
        Vector3d(13.529902503654933 * hs, 210.35533905932738 * hs, -32.66407412190941 * hs),
        Vector3d(24.999999999999996 * hs, 210.35533905932738 * hs, -25.000000000000007 * hs),
        Vector3d(32.66407412190941 * hs, 210.35533905932738 * hs, -13.529902503654947 * hs),
        Vector3d(46.19397662556434 * hs, 194.13417161825447 * hs, 0.0 * hs),
        Vector3d(42.67766952966369 * hs, 194.13417161825447 * hs, 17.67766952966369 * hs),
        Vector3d(32.664074121909415 * hs, 194.13417161825447 * hs, 32.66407412190941 * hs),
        Vector3d(17.677669529663692 * hs, 194.13417161825447 * hs, 42.67766952966369 * hs),
        Vector3d(2.828565280719251E-15 * hs, 194.13417161825447 * hs, 46.19397662556434 * hs),
        Vector3d(-17.677669529663685 * hs, 194.13417161825447 * hs, 42.67766952966369 * hs),
        Vector3d(-32.66407412190941 * hs, 194.13417161825447 * hs, 32.664074121909415 * hs),
        Vector3d(-42.67766952966369 * hs, 194.13417161825447 * hs, 17.677669529663696 * hs),
        Vector3d(-46.19397662556434 * hs, 194.13417161825447 * hs, 5.657130561438502E-15 * hs),
        Vector3d(-42.677669529663696 * hs, 194.13417161825447 * hs, -17.677669529663685 * hs),
        Vector3d(-32.66407412190942 * hs, 194.13417161825447 * hs, -32.66407412190941 * hs),
        Vector3d(-17.677669529663714 * hs, 194.13417161825447 * hs, -42.67766952966368 * hs),
        Vector3d(-8.485695842157753E-15 * hs, 194.13417161825447 * hs, -46.19397662556434 * hs),
        Vector3d(17.6776695296637 * hs, 194.13417161825447 * hs, -42.67766952966368 * hs),
        Vector3d(32.66407412190941 * hs, 194.13417161825447 * hs, -32.66407412190942 * hs),
        Vector3d(42.67766952966368 * hs, 194.13417161825447 * hs, -17.677669529663717 * hs),
        Vector3d(50.0 * hs, 175.0 * hs, 0.0 * hs),
        Vector3d(46.19397662556434 * hs, 175.0 * hs, 19.134171618254488 * hs),
        Vector3d(35.35533905932738 * hs, 175.0 * hs, 35.35533905932737 * hs),
        Vector3d(19.13417161825449 * hs, 175.0 * hs, 46.19397662556434 * hs),
        Vector3d(3.061616997868383E-15 * hs, 175.0 * hs, 50.0 * hs),
        Vector3d(-19.134171618254488 * hs, 175.0 * hs, 46.19397662556434 * hs),
        Vector3d(-35.35533905932737 * hs, 175.0 * hs, 35.35533905932738 * hs),
        Vector3d(-46.19397662556434 * hs, 175.0 * hs, 19.134171618254495 * hs),
        Vector3d(-50.0 * hs, 175.0 * hs, 6.123233995736766E-15 * hs),
        Vector3d(-46.19397662556434 * hs, 175.0 * hs, -19.134171618254484 * hs),
        Vector3d(-35.355339059327385 * hs, 175.0 * hs, -35.35533905932737 * hs),
        Vector3d(-19.134171618254516 * hs, 175.0 * hs, -46.193976625564325 * hs),
        Vector3d(-9.184850993605149E-15 * hs, 175.0 * hs, -50.0 * hs),
        Vector3d(19.1341716182545 * hs, 175.0 * hs, -46.19397662556433 * hs),
        Vector3d(35.35533905932737 * hs, 175.0 * hs, -35.355339059327385 * hs),
        Vector3d(46.193976625564325 * hs, 175.0 * hs, -19.13417161825452 * hs),
        Vector3d(50.0 * hs, 50.0 * hs, 0.0 * hs),
        Vector3d(46.19397662556434 * hs, 50.0 * hs, 19.134171618254488 * hs),
        Vector3d(35.35533905932738 * hs, 50.0 * hs, 35.35533905932737 * hs),
        Vector3d(19.13417161825449 * hs, 50.0 * hs, 46.19397662556434 * hs),
        Vector3d(3.061616997868383E-15 * hs, 50.0 * hs, 50.0 * hs),
        Vector3d(-19.134171618254488 * hs, 50.0 * hs, 46.19397662556434 * hs),
        Vector3d(-35.35533905932737 * hs, 50.0 * hs, 35.35533905932738 * hs),
        Vector3d(-46.19397662556434 * hs, 50.0 * hs, 19.134171618254495 * hs),
        Vector3d(-50.0 * hs, 50.0 * hs, 6.123233995736766E-15 * hs),
        Vector3d(-46.19397662556434 * hs, 50.0 * hs, -19.134171618254484 * hs),
        Vector3d(-35.355339059327385 * hs, 50.0 * hs, -35.35533905932737 * hs),
        Vector3d(-19.134171618254516 * hs, 50.0 * hs, -46.193976625564325 * hs),
        Vector3d(-9.184850993605149E-15 * hs, 50.0 * hs, -50.0 * hs),
        Vector3d(19.1341716182545 * hs, 50.0 * hs, -46.19397662556433 * hs),
        Vector3d(35.35533905932737 * hs, 50.0 * hs, -35.355339059327385 * hs),
        Vector3d(46.193976625564325 * hs, 50.0 * hs, -19.13417161825452 * hs),
        Vector3d(46.19397662556434 * hs, 30.865828381745516 * hs, 0.0 * hs),
        Vector3d(42.67766952966369 * hs, 30.865828381745516 * hs, 17.67766952966369 * hs),
        Vector3d(32.664074121909415 * hs, 30.865828381745516 * hs, 32.66407412190941 * hs),
        Vector3d(17.677669529663692 * hs, 30.865828381745516 * hs, 42.67766952966369 * hs),
        Vector3d(2.828565280719251E-15 * hs, 30.865828381745516 * hs, 46.19397662556434 * hs),
        Vector3d(-17.677669529663685 * hs, 30.865828381745516 * hs, 42.67766952966369 * hs),
        Vector3d(-32.66407412190941 * hs, 30.865828381745516 * hs, 32.664074121909415 * hs),
        Vector3d(-42.67766952966369 * hs, 30.865828381745516 * hs, 17.677669529663696 * hs),
        Vector3d(-46.19397662556434 * hs, 30.865828381745516 * hs, 5.657130561438502E-15 * hs),
        Vector3d(-42.677669529663696 * hs, 30.865828381745516 * hs, -17.677669529663685 * hs),
        Vector3d(-32.66407412190942 * hs, 30.865828381745516 * hs, -32.66407412190941 * hs),
        Vector3d(-17.677669529663714 * hs, 30.865828381745516 * hs, -42.67766952966368 * hs),
        Vector3d(-8.485695842157753E-15 * hs, 30.865828381745516 * hs, -46.19397662556434 * hs),
        Vector3d(17.6776695296637 * hs, 30.865828381745516 * hs, -42.67766952966368 * hs),
        Vector3d(32.66407412190941 * hs, 30.865828381745516 * hs, -32.66407412190942 * hs),
        Vector3d(42.67766952966368 * hs, 30.865828381745516 * hs, -17.677669529663717 * hs),
        Vector3d(35.35533905932738 * hs, 14.644660940672622 * hs, 0.0 * hs),
        Vector3d(32.664074121909415 * hs, 14.644660940672622 * hs, 13.529902503654926 * hs),
        Vector3d(25.000000000000004 * hs, 14.644660940672622 * hs, 25.0 * hs),
        Vector3d(13.529902503654927 * hs, 14.644660940672622 * hs, 32.664074121909415 * hs),
        Vector3d(2.164890140588733E-15 * hs, 14.644660940672622 * hs, 35.35533905932738 * hs),
        Vector3d(-13.529902503654924 * hs, 14.644660940672622 * hs, 32.664074121909415 * hs),
        Vector3d(-25.0 * hs, 14.644660940672622 * hs, 25.000000000000004 * hs),
        Vector3d(-32.664074121909415 * hs, 14.644660940672622 * hs, 13.52990250365493 * hs),
        Vector3d(-35.35533905932738 * hs, 14.644660940672622 * hs, 4.329780281177466E-15 * hs),
        Vector3d(-32.664074121909415 * hs, 14.644660940672622 * hs, -13.529902503654922 * hs),
        Vector3d(-25.000000000000007 * hs, 14.644660940672622 * hs, -25.0 * hs),
        Vector3d(-13.529902503654945 * hs, 14.644660940672622 * hs, -32.66407412190941 * hs),
        Vector3d(-6.4946704217662E-15 * hs, 14.644660940672622 * hs, -35.35533905932738 * hs),
        Vector3d(13.529902503654933 * hs, 14.644660940672622 * hs, -32.66407412190941 * hs),
        Vector3d(24.999999999999996 * hs, 14.644660940672622 * hs, -25.000000000000007 * hs),
        Vector3d(32.66407412190941 * hs, 14.644660940672622 * hs, -13.529902503654947 * hs),
        Vector3d(19.13417161825449 * hs, 3.8060233744356537 * hs, 0.0 * hs),
        Vector3d(17.67766952966369 * hs, 3.8060233744356537 * hs, 7.322330470336313 * hs),
        Vector3d(13.529902503654927 * hs, 3.8060233744356537 * hs, 13.529902503654926 * hs),
        Vector3d(7.322330470336314 * hs, 3.8060233744356537 * hs, 17.67766952966369 * hs),
        Vector3d(1.1716301013315748E-15 * hs, 3.8060233744356537 * hs, 19.13417161825449 * hs),
        Vector3d(-7.322330470336312 * hs, 3.8060233744356537 * hs, 17.67766952966369 * hs),
        Vector3d(-13.529902503654926 * hs, 3.8060233744356537 * hs, 13.529902503654927 * hs),
        Vector3d(-17.67766952966369 * hs, 3.8060233744356537 * hs, 7.322330470336316 * hs),
        Vector3d(-19.13417161825449 * hs, 3.8060233744356537 * hs, 2.3432602026631496E-15 * hs),
        Vector3d(-17.677669529663692 * hs, 3.8060233744356537 * hs, -7.322330470336311 * hs),
        Vector3d(-13.52990250365493 * hs, 3.8060233744356537 * hs, -13.529902503654926 * hs),
        Vector3d(-7.322330470336324 * hs, 3.8060233744356537 * hs, -17.677669529663685 * hs),
        Vector3d(-3.514890303994724E-15 * hs, 3.8060233744356537 * hs, -19.13417161825449 * hs),
        Vector3d(7.3223304703363175 * hs, 3.8060233744356537 * hs, -17.67766952966369 * hs),
        Vector3d(13.529902503654924 * hs, 3.8060233744356537 * hs, -13.52990250365493 * hs),
        Vector3d(17.677669529663685 * hs, 3.8060233744356537 * hs, -7.322330470336325 * hs),
        Vector3d(0.0 * hs, 0.0 * hs, 0.0 * hs),
    )
    val faces: List<List<Int>> = listOf(
        listOf(0, 2, 1),
        listOf(0, 3, 2),
        listOf(0, 4, 3),
        listOf(0, 5, 4),
        listOf(0, 6, 5),
        listOf(0, 7, 6),
        listOf(0, 8, 7),
        listOf(0, 9, 8),
        listOf(0, 10, 9),
        listOf(0, 11, 10),
        listOf(0, 12, 11),
        listOf(0, 13, 12),
        listOf(0, 14, 13),
        listOf(0, 15, 14),
        listOf(0, 16, 15),
        listOf(0, 1, 16),
        listOf(1, 2, 18, 17),
        listOf(2, 3, 19, 18),
        listOf(3, 4, 20, 19),
        listOf(4, 5, 21, 20),
        listOf(5, 6, 22, 21),
        listOf(6, 7, 23, 22),
        listOf(7, 8, 24, 23),
        listOf(8, 9, 25, 24),
        listOf(9, 10, 26, 25),
        listOf(10, 11, 27, 26),
        listOf(11, 12, 28, 27),
        listOf(12, 13, 29, 28),
        listOf(13, 14, 30, 29),
        listOf(14, 15, 31, 30),
        listOf(15, 16, 32, 31),
        listOf(16, 1, 17, 32),
        listOf(17, 18, 34, 33),
        listOf(18, 19, 35, 34),
        listOf(19, 20, 36, 35),
        listOf(20, 21, 37, 36),
        listOf(21, 22, 38, 37),
        listOf(22, 23, 39, 38),
        listOf(23, 24, 40, 39),
        listOf(24, 25, 41, 40),
        listOf(25, 26, 42, 41),
        listOf(26, 27, 43, 42),
        listOf(27, 28, 44, 43),
        listOf(28, 29, 45, 44),
        listOf(29, 30, 46, 45),
        listOf(30, 31, 47, 46),
        listOf(31, 32, 48, 47),
        listOf(32, 17, 33, 48),
        listOf(33, 34, 50, 49),
        listOf(34, 35, 51, 50),
        listOf(35, 36, 52, 51),
        listOf(36, 37, 53, 52),
        listOf(37, 38, 54, 53),
        listOf(38, 39, 55, 54),
        listOf(39, 40, 56, 55),
        listOf(40, 41, 57, 56),
        listOf(41, 42, 58, 57),
        listOf(42, 43, 59, 58),
        listOf(43, 44, 60, 59),
        listOf(44, 45, 61, 60),
        listOf(45, 46, 62, 61),
        listOf(46, 47, 63, 62),
        listOf(47, 48, 64, 63),
        listOf(48, 33, 49, 64),
        listOf(50, 66, 65, 49),
        listOf(51, 67, 66, 50),
        listOf(52, 68, 67, 51),
        listOf(53, 69, 68, 52),
        listOf(54, 70, 69, 53),
        listOf(55, 71, 70, 54),
        listOf(56, 72, 71, 55),
        listOf(57, 73, 72, 56),
        listOf(58, 74, 73, 57),
        listOf(59, 75, 74, 58),
        listOf(60, 76, 75, 59),
        listOf(61, 77, 76, 60),
        listOf(62, 78, 77, 61),
        listOf(63, 79, 78, 62),
        listOf(64, 80, 79, 63),
        listOf(49, 65, 80, 64),
        listOf(66, 82, 81, 65),
        listOf(67, 83, 82, 66),
        listOf(68, 84, 83, 67),
        listOf(69, 85, 84, 68),
        listOf(70, 86, 85, 69),
        listOf(71, 87, 86, 70),
        listOf(72, 88, 87, 71),
        listOf(73, 89, 88, 72),
        listOf(74, 90, 89, 73),
        listOf(75, 91, 90, 74),
        listOf(76, 92, 91, 75),
        listOf(77, 93, 92, 76),
        listOf(78, 94, 93, 77),
        listOf(79, 95, 94, 78),
        listOf(80, 96, 95, 79),
        listOf(65, 81, 96, 80),
        listOf(82, 98, 97, 81),
        listOf(83, 99, 98, 82),
        listOf(84, 100, 99, 83),
        listOf(85, 101, 100, 84),
        listOf(86, 102, 101, 85),
        listOf(87, 103, 102, 86),
        listOf(88, 104, 103, 87),
        listOf(89, 105, 104, 88),
        listOf(90, 106, 105, 89),
        listOf(91, 107, 106, 90),
        listOf(92, 108, 107, 91),
        listOf(93, 109, 108, 92),
        listOf(94, 110, 109, 93),
        listOf(95, 111, 110, 94),
        listOf(96, 112, 111, 95),
        listOf(81, 97, 112, 96),
        listOf(98, 114, 113, 97),
        listOf(99, 115, 114, 98),
        listOf(100, 116, 115, 99),
        listOf(101, 117, 116, 100),
        listOf(102, 118, 117, 101),
        listOf(103, 119, 118, 102),
        listOf(104, 120, 119, 103),
        listOf(105, 121, 120, 104),
        listOf(106, 122, 121, 105),
        listOf(107, 123, 122, 106),
        listOf(108, 124, 123, 107),
        listOf(109, 125, 124, 108),
        listOf(110, 126, 125, 109),
        listOf(111, 127, 126, 110),
        listOf(112, 128, 127, 111),
        listOf(97, 113, 128, 112),
        listOf(113, 114, 129),
        listOf(114, 115, 129),
        listOf(115, 116, 129),
        listOf(116, 117, 129),
        listOf(117, 118, 129),
        listOf(118, 119, 129),
        listOf(119, 120, 129),
        listOf(120, 121, 129),
        listOf(121, 122, 129),
        listOf(122, 123, 129),
        listOf(123, 124, 129),
        listOf(124, 125, 129),
        listOf(125, 126, 129),
        listOf(126, 127, 129),
        listOf(127, 128, 129),
        listOf(128, 113, 129),
    )
    val uvs: List<List<Vector3d>> = faces.map { face ->
        when(face.size) {
            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))
            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))
            else -> face.map { Vector3d(0.0,0.0,0.0) }
        }
    }
    return Mesh(vertices, faces, uvs, color)
}

fun createMapMesh(size: Double, color: Color): Mesh {
    val hs = (size * 10.0) / 100.0

    val vertices = listOf(
        Vector3d(-75.0 * hs, 0.0 * hs, -25.0 * hs),
        Vector3d(-25.0 * hs, 0.0 * hs, -25.0 * hs),
        Vector3d(-25.0 * hs, 0.0 * hs, 25.0 * hs),
        Vector3d(-75.0 * hs, 0.0 * hs, 25.0 * hs),
        Vector3d(-25.0 * hs, 0.0 * hs, -25.0 * hs),
        Vector3d(25.0 * hs, 0.0 * hs, -25.0 * hs),
        Vector3d(25.0 * hs, 0.0 * hs, 25.0 * hs),
        Vector3d(-25.0 * hs, 0.0 * hs, 25.0 * hs),
        Vector3d(25.0 * hs, 0.0 * hs, -25.0 * hs),
        Vector3d(75.0 * hs, 0.0 * hs, -25.0 * hs),
        Vector3d(75.0 * hs, 0.0 * hs, 25.0 * hs),
        Vector3d(25.0 * hs, 0.0 * hs, 25.0 * hs),
        Vector3d(-75.0 * hs, 50.0 * hs, -25.0 * hs),
        Vector3d(-25.0 * hs, 50.0 * hs, -25.0 * hs),
        Vector3d(25.0 * hs, 50.0 * hs, -25.0 * hs),
        Vector3d(75.0 * hs, 50.0 * hs, -25.0 * hs),
        Vector3d(-75.0 * hs, 50.0 * hs, 25.0 * hs),
        Vector3d(-25.0 * hs, 50.0 * hs, 25.0 * hs),
        Vector3d(25.0 * hs, 50.0 * hs, 25.0 * hs),
        Vector3d(75.0 * hs, 50.0 * hs, 25.0 * hs),
    )

    val faces: List<List<Int>> = listOf(
        listOf(1, 2, 3, 0),
        listOf(2, 1, 0, 3),
        listOf(5, 6, 7, 4),
        listOf(6, 5, 4, 7),
        listOf(9, 10, 11, 8),
        listOf(10, 9, 8, 11),
        listOf(0, 1, 13, 12),
        listOf(1, 5, 14, 13),
        listOf(5, 9, 15, 14),
        listOf(9, 10, 19, 15),
        listOf(10, 6, 18, 19),
        listOf(6, 2, 17, 18),
        listOf(2, 3, 16, 17),
        listOf(3, 0, 12, 16),
        listOf(1, 2, 17, 13),
        listOf(2, 1, 13, 17),
        listOf(5, 6, 18, 14),
        listOf(6, 5, 14, 18),
        listOf(3, 2, 17, 16),
        listOf(2, 6, 18, 17),
        listOf(6, 10, 19, 18),
        listOf(10, 9, 15, 19),
        listOf(9, 5, 14, 15),
        listOf(5, 1, 13, 14),
        listOf(1, 0, 12, 13),
        listOf(0, 3, 16, 12),
    )

    data class Edge(val a: Int, val b: Int)
    val edges: List<Edge> = listOf(
        Edge(a=0, b=1),
        Edge(a=1, b=2),
        Edge(a=2, b=3),
        Edge(a=3, b=0),
        Edge(a=4, b=5),
        Edge(a=5, b=6),
        Edge(a=6, b=7),
        Edge(a=7, b=4),
        Edge(a=8, b=9),
        Edge(a=9, b=10),
        Edge(a=10, b=11),
        Edge(a=11, b=8),
        Edge(a=19, b=18),
        Edge(a=18, b=17),
        Edge(a=17, b=16),
        Edge(a=16, b=12),
        Edge(a=12, b=13),
        Edge(a=13, b=14),
        Edge(a=14, b=15),
        Edge(a=15, b=19),
        Edge(a=18, b=14),
        Edge(a=13, b=17),
        Edge(a=1, b=13),
        Edge(a=12, b=0),
        Edge(a=5, b=14),
        Edge(a=15, b=9),
        Edge(a=10, b=19),
        Edge(a=18, b=6),
        Edge(a=2, b=17),
        Edge(a=16, b=3),
    )

    val uvs: List<List<Vector3d>> = faces.map { face ->
        when(face.size) {
            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))
            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))
            else -> face.map { Vector3d(0.0,0.0,0.0) }
        }
    }

    val textureMapping = mapOf(
        0 to "floor",
        1 to "floor",
        2 to "floor",

        3 to "floor",
        4 to "floor",
        5 to "floor",

        6 to "ceiling",
        7 to "ceiling",
        8 to "ceiling",

        9 to "ceiling",

        10 to "ceiling",
        11 to "ceiling",
        12 to "ceiling",

        13 to "ceiling",

        14 to "bricks",
        15 to "bricks",
        16 to "bricks",
        17 to "bricks",

        18 to "blackBricks",
        19 to "blackBricks",
        20 to "blackBricks",

        21 to "blackBricks",

        22 to "blackBricks",
        23 to "blackBricks",
        24 to "blackBricks",

        25 to "blackBricks",
    )

    val blushes = listOf(
        AABB(
            min = Vector3d(-29.0 * hs, 10.0 * hs, -5.0 * hs),
            max = Vector3d(-21.0 * hs, 0.001 * hs , 5.0 * hs),
        ),
        AABB(
            min = Vector3d(29.0 * hs, 10.0 * hs, -5.0 * hs),
            max = Vector3d(21.9 * hs, 0.001 * hs , 5.0 * hs),
        )
    )

    return Mesh(vertices, faces, uvs, color, blushes = blushes, faceTextureNames = textureMapping)
}

fun createCustomMesh(size: Double, color: Color): Mesh {
    val hs = size / 100.0

    val vertices = listOf(
        Vector3d(-50.0 * hs, 0.0 * hs, -50.0 * hs),
        Vector3d(50.0 * hs, 0.0 * hs, -50.0 * hs),
        Vector3d(50.0 * hs, 0.0 * hs, 50.0 * hs),
        Vector3d(-50.0 * hs, 0.0 * hs, 50.0 * hs),
        Vector3d(-50.0 * hs, 100.0 * hs, -50.0 * hs),
        Vector3d(50.0 * hs, 100.0 * hs, -50.0 * hs),
        Vector3d(50.0 * hs, 25.0 * hs, 50.0 * hs),
        Vector3d(-50.0 * hs, 20.0 * hs, 50.0 * hs),
    )

    val faces: List<List<Int>> = listOf(
        listOf(3, 2, 6, 7),
        listOf(1, 0, 4, 5),
        listOf(2, 1, 5, 6),
        listOf(0, 3, 7, 4),
        listOf(7, 6, 5, 4),
        listOf(0, 1, 2, 3),
    )

    data class Edge(val a: Int, val b: Int)
    val edges: List<Edge> = listOf(
        Edge(a=0, b=1),
        Edge(a=1, b=2),
        Edge(a=2, b=3),
        Edge(a=3, b=0),
        Edge(a=4, b=5),
        Edge(a=5, b=6),
        Edge(a=6, b=7),
        Edge(a=7, b=4),
        Edge(a=0, b=4),
        Edge(a=1, b=5),
        Edge(a=2, b=6),
        Edge(a=3, b=7),
    )

    val uvs: List<List<Vector3d>> = faces.map { face ->
        when(face.size) {
            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))
            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))
            else -> face.map { Vector3d(0.0,0.0,0.0) }
        }
    }
    return Mesh(vertices, faces, uvs, color)
}
