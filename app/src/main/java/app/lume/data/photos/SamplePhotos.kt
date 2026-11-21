package app.lume.data.photos

import app.lume.R

/**
 * Fotos de exemplo do app (Fase 1: o pack cresce de 4 para 12).
 * As 4 de sempre (retrato, paisagem, rua, café) + 8 curadas do
 * Pixabay (licença livre para uso comercial, sem atribuição —
 * Pixabay License), baixadas uma vez e embutidas no app: o
 * usuário não chama API nenhuma e tudo funciona offline.
 *
 * A lista é a fonte única: os atalhos do "Começar", a fileira
 * "Fotos de exemplo" da Home e os cartões de preset (que usam
 * [portrait] e [landscape] como reserva) lêem daqui.
 */
object SamplePhotos {

    val portrait: Int = R.drawable.sample_portrait
    val landscape: Int = R.drawable.sample_landscape
    val street: Int = R.drawable.sample_street
    val coffee: Int = R.drawable.sample_coffee

    /** Árvore solitária ao pôr do sol (Pixabay). */
    val tree: Int = R.drawable.sample_tree

    /** Estrada de outono na mata (Pixabay). */
    val road: Int = R.drawable.sample_road

    /** Caminho de mata com sol (Pixabay). */
    val avenue: Int = R.drawable.sample_avenue

    /** Homem escrevendo no caderno (Pixabay). */
    val business: Int = R.drawable.sample_business

    /** Mesa rosa com teclado e fone (Pixabay). */
    val desk: Int = R.drawable.sample_desk

    /** Hambúrguer com fogo ao fundo (Pixabay). */
    val burger: Int = R.drawable.sample_burger

    /** Céu estrelado, Via Láctea (Pixabay). */
    val night: Int = R.drawable.sample_night

    /** Montanhas nevadas com trilha (Pixabay). */
    val mountain: Int = R.drawable.sample_mountain

    /** As 12, na ordem da fileira da Home. */
    val all: List<Int> = listOf(
        portrait,
        landscape,
        street,
        coffee,
        tree,
        road,
        avenue,
        business,
        desk,
        burger,
        night,
        mountain
    )
}
