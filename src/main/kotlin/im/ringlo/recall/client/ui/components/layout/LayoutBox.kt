package im.ringlo.recall.client.ui.components.layout

data class LayoutBox(var x: Int = 0, var y: Int = 0, var width: Int = 0, var height: Int = 0) {
    val right: Int
        get() = x + width

    val bottom: Int
        get() = y + height

    fun contains(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseY >= y && mouseX < right && mouseY < bottom

    fun inset(amount: Int): LayoutBox =
        LayoutBox(x + amount, y + amount, width - amount * 2, height - amount * 2)
}
