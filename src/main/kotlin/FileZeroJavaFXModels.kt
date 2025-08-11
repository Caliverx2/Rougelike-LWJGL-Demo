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
        listOf(4, 7, 6, 5),
        listOf(3, 2, 6, 7),
        listOf(0, 4, 5, 1),
        listOf(1, 5, 6, 2),
        listOf(4, 0, 3, 7)

    )
    if (inverted) {
        faces = faces.map { it.reversed() }
    }
    val uvs = listOf(
        listOf(Vector3d(0.0, 1.0, 0.0), Vector3d(1.0, 1.0, 0.0), Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 0.0, 0.0)),
        listOf(Vector3d(0.0, 1.0, 0.0), Vector3d(0.0, 0.0, 0.0), Vector3d(1.0, 0.0, 0.0), Vector3d(1.0, 1.0, 0.0)),
        listOf(Vector3d(0.0, 1.0, 0.0), Vector3d(1.0, 1.0, 0.0), Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 0.0, 0.0)),
        listOf(Vector3d(0.0, 0.0, 0.0), Vector3d(1.0, 0.0, 0.0), Vector3d(1.0, 1.0, 0.0), Vector3d(0.0, 1.0, 0.0)),
        listOf(Vector3d(0.0, 1.0, 0.0), Vector3d(1.0, 1.0, 0.0), Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 0.0, 0.0)),
        listOf(Vector3d(1.0, 1.0, 0.0), Vector3d(0.0, 1.0, 0.0), Vector3d(0.0, 0.0, 0.0), Vector3d(1.0, 0.0, 0.0))
    )
    return Mesh(vertices, faces, uvs, color)
}

fun createPyramidMesh(size: Double, height: Double, color: Color): Mesh {
    val hs = size / 2.0
    val vertices = listOf(
        Vector3d(-hs, 0.0, hs),   // 0 front-left
        Vector3d(hs, 0.0, hs),    // 1 front-right
        Vector3d(hs, 0.0, -hs),   // 2 back-right
        Vector3d(-hs, 0.0, -hs),  // 3 back-left
        Vector3d(0.0, height, 0.0) // 4 top
    )
    val faces = listOf(
        listOf(0, 3, 2, 1), // podstawa
        listOf(0, 1, 4),    // ściana frontowa
        listOf(1, 2, 4),    // prawa
        listOf(2, 3, 4),    // tył
        listOf(3, 0, 4)     // lewa
    )
    val uvs = listOf(
        listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0)),
        listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0)),
        listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0)),
        listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0)),
        listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))
    )
    return Mesh(vertices, faces, uvs, color)
}

fun createInvertedPyramidMesh(size: Double, height: Double, color: Color): Mesh {
    val hs = size / 2.0
    val vertices = listOf(
        Vector3d(-hs, height, hs),    // 0 front-left (góra)
        Vector3d(hs, height, hs),     // 1 front-right (góra)
        Vector3d(hs, height, -hs),    // 2 back-right (góra)
        Vector3d(-hs, height, -hs),   // 3 back-left (góra)
        Vector3d(0.0, 0.0, 0.0) // 4 dół (szpic)
    )
    val faces = listOf(
        listOf(0, 1, 2, 3), // podstawa
        listOf(0, 4, 1),    // front
        listOf(1, 4, 2),    // prawa
        listOf(2, 4, 3),    // tył
        listOf(3, 4, 0)     // lewa
    )
    val uvs = listOf(
        listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0)),
        listOf(Vector3d(0.0,0.0,0.0), Vector3d(0.5,1.0,0.0), Vector3d(1.0,0.0,0.0)),
        listOf(Vector3d(0.0,0.0,0.0), Vector3d(0.5,1.0,0.0), Vector3d(1.0,0.0,0.0)),
        listOf(Vector3d(0.0,0.0,0.0), Vector3d(0.5,1.0,0.0), Vector3d(1.0,0.0,0.0)),
        listOf(Vector3d(0.0,0.0,0.0), Vector3d(0.5,1.0,0.0), Vector3d(1.0,0.0,0.0))
    )
    return Mesh(vertices, faces, uvs, color)
}

fun createTowerMesh(color: Color): Mesh {
    val vertices = listOf(
        Vector3d(-50.0, 0.0, -50.0),
        Vector3d(-50.0, 0.0, 50.0),
        Vector3d(50.0, 0.0, -50.0),
        Vector3d(-50.0, 50.0, -50.0),
        Vector3d(50.0, 0.0, 50.0),
        Vector3d(50.0, 50.0, -50.0),
        Vector3d(-50.0, 50.0, 50.0),
        Vector3d(50.0, 50.0, 50.0),
        Vector3d(-20.0, 50.0, -20.0),
        Vector3d(20.0, 50.0, -20.0),
        Vector3d(20.0, 50.0, 20.0),
        Vector3d(-20.0, 50.0, 20.0),
        Vector3d(-20.0, 100.0, -20.0),
        Vector3d(20.0, 100.0, -20.0),
        Vector3d(-20.0, 100.0, 20.0),
        Vector3d(20.0, 100.0, 20.0),
    )
    val faces: List<List<Int>> = listOf(
        listOf(7, 5, 3, 6),
        listOf(5, 2, 0, 3),
        listOf(3, 0, 1, 6),
        listOf(6, 1, 4, 7),
        listOf(7, 4, 2, 5),
        listOf(4, 1, 0, 2),
        listOf(12, 8, 11, 14),
        listOf(14, 11, 10, 15),
        listOf(15, 10, 9, 13),
        listOf(13, 9, 8, 12),
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

fun createKotlinModelMesh(color: Color): Mesh {
    val vertices = listOf(
        Vector3d(-50.0, 0.0, -50.0),
        Vector3d(50.0, 0.0, -50.0),
        Vector3d(0.0, 20.0, 0.0),
        Vector3d(0.0, 0.0, 0.0),
        Vector3d(50.0, 0.0, 0.0),
        Vector3d(-50.0, 0.0, 0.0),
        Vector3d(-50.0, 0.0, 50.0),
        Vector3d(50.0, 0.0, 50.0),
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

fun createTankMesh(color: Color): Mesh {
    val vertices = listOf(
        Vector3d(-30.0, 0.0, -40.0),
        Vector3d(30.0, 0.0, -40.0),
        Vector3d(30.0, 0.0, 40.0),
        Vector3d(-30.0, 0.0, 40.0),
        Vector3d(-40.0, 20.0, -60.0),
        Vector3d(-40.0, 20.0, 50.0),
        Vector3d(40.0, 20.0, 50.0),
        Vector3d(40.0, 20.0, -60.0),
        Vector3d(-20.0, 40.0, -20.0),
        Vector3d(20.0, 40.0, -20.0),
        Vector3d(20.0, 40.0, 40.0),
        Vector3d(-20.0, 40.0, 40.0),
        Vector3d(20.0, 60.0, 0.0),
        Vector3d(-20.0, 60.0, 0.0),
        Vector3d(-10.0, 40.0, -20.0),
        Vector3d(10.0, 40.0, -20.0),
        Vector3d(-10.0, 50.0, -10.0),
        Vector3d(10.0, 50.0, -10.0),
        Vector3d(-10.0, 50.0, -100.0),
        Vector3d(10.0, 50.0, -100.0),
        Vector3d(-10.0, 40.0, -100.0),
        Vector3d(10.0, 40.0, -100.0),
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
        listOf(20, 14, 15, 21),
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

fun createOffroadCarMesh(color: Color): Mesh {
    val vertices = listOf(
        Vector3d(20.0, 10.0, -40.0),
        Vector3d(-20.0, 10.0, -40.0),
        Vector3d(-20.0, 10.0, 40.0),
        Vector3d(20.0, 10.0, 40.0),
        Vector3d(20.0, 30.0, -40.0),
        Vector3d(-20.0, 30.0, -40.0),
        Vector3d(-20.0, 30.0, 25.0),
        Vector3d(20.0, 30.0, 25.0),
        Vector3d(18.0, 30.0, -30.0),
        Vector3d(-18.0, 30.0, -30.0),
        Vector3d(-18.0, 45.0, -20.0),
        Vector3d(18.0, 45.0, -20.0),
        Vector3d(20.0, 30.0, 22.0),
        Vector3d(-20.0, 30.0, 22.0),
        Vector3d(-20.0, 50.0, 22.0),
        Vector3d(20.0, 50.0, 22.0),
        Vector3d(29.0, 30.0, -25.0), Vector3d(29.0, 25.6, -35.6), Vector3d(29.0, 15.0, -40.0), Vector3d(29.0, 4.4, -35.6), Vector3d(29.0, 0.0, -25.0), Vector3d(29.0, 4.4, -14.4), Vector3d(29.0, 15.0, -10.0), Vector3d(29.0, 25.6, -14.4),
        Vector3d(21.0, 30.0, -25.0), Vector3d(21.0, 25.6, -35.6), Vector3d(21.0, 15.0, -40.0), Vector3d(21.0, 4.4, -35.6), Vector3d(21.0, 0.0, -25.0), Vector3d(21.0, 4.4, -14.4), Vector3d(21.0, 15.0, -10.0), Vector3d(21.0, 25.6, -14.4),
        Vector3d(-29.0, 30.0, -25.0), Vector3d(-29.0, 25.6, -35.6), Vector3d(-29.0, 15.0, -40.0), Vector3d(-29.0, 4.4, -35.6), Vector3d(-29.0, 0.0, -25.0), Vector3d(-29.0, 4.4, -14.4), Vector3d(-29.0, 15.0, -10.0), Vector3d(-29.0, 25.6, -14.4),
        Vector3d(-21.0, 30.0, -25.0), Vector3d(-21.0, 25.6, -35.6), Vector3d(-21.0, 15.0, -40.0), Vector3d(-21.0, 4.4, -35.6), Vector3d(-21.0, 0.0, -25.0), Vector3d(-21.0, 4.4, -14.4), Vector3d(-21.0, 15.0, -10.0), Vector3d(-21.0, 25.6, -14.4),
        Vector3d(29.0, 30.0, 25.0), Vector3d(29.0, 25.6, 14.4), Vector3d(29.0, 15.0, 10.0), Vector3d(29.0, 4.4, 14.4), Vector3d(29.0, 0.0, 25.0), Vector3d(29.0, 4.4, 35.6), Vector3d(29.0, 15.0, 40.0), Vector3d(29.0, 25.6, 35.6),
        Vector3d(21.0, 30.0, 25.0), Vector3d(21.0, 25.6, 14.4), Vector3d(21.0, 15.0, 10.0), Vector3d(21.0, 4.4, 14.4), Vector3d(21.0, 0.0, 25.0), Vector3d(21.0, 4.4, 35.6), Vector3d(21.0, 15.0, 40.0), Vector3d(21.0, 25.6, 35.6),
        Vector3d(-29.0, 30.0, 25.0), Vector3d(-29.0, 25.6, 14.4), Vector3d(-29.0, 15.0, 10.0), Vector3d(-29.0, 4.4, 14.4), Vector3d(-29.0, 0.0, 25.0), Vector3d(-29.0, 4.4, 35.6), Vector3d(-29.0, 15.0, 40.0), Vector3d(-29.0, 25.6, 35.6),
        Vector3d(-21.0, 30.0, 25.0), Vector3d(-21.0, 25.6, 14.4), Vector3d(-21.0, 15.0, 10.0), Vector3d(-21.0, 4.4, 14.4), Vector3d(-21.0, 0.0, 25.0), Vector3d(-21.0, 4.4, 35.6), Vector3d(-21.0, 15.0, 40.0), Vector3d(-21.0, 25.6, 35.6)
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

fun createStairMesh(color: Color): Mesh {
    val vertices = listOf(
        Vector3d(-50.0, 0.0, -50.0),
        Vector3d(50.0, 0.0, -50.0),
        Vector3d(50.0, 0.0, 50.0),
        Vector3d(-50.0, 0.0, 50.0),
        Vector3d(50.0, 100.0, 50.0),
        Vector3d(-50.0, 100.0, 50.0),
        Vector3d(50.0, 50.0, -50.0),
        Vector3d(-50.0, 50.0, -50.0),
        Vector3d(-50.0, 0.0, 0.0),
        Vector3d(50.0, 0.0, 0.0),
        Vector3d(-50.0, 50.0, 0.0),
        Vector3d(50.0, 50.0, 0.0),
        Vector3d(-50.0, 100.0, 0.0),
        Vector3d(50.0, 100.0, 0.0),
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
    val uvs: List<List<Vector3d>> = faces.map { face ->
        when(face.size) {
            3 -> listOf(Vector3d(0.0,0.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.5,1.0,0.0))
            4 -> listOf(Vector3d(0.0,1.0,0.0), Vector3d(1.0,1.0,0.0), Vector3d(1.0,0.0,0.0), Vector3d(0.0,0.0,0.0))
            else -> face.map { Vector3d(0.0,0.0,0.0) }
        }
    }
    return Mesh(vertices, faces, uvs, color)
}