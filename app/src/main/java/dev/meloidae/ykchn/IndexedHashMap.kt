package dev.meloidae.ykchn

class IndexedHashMap<K, V> : HashMap<K, V>() {
    private val indexedKeys: MutableList<K> = mutableListOf()

    override fun put(key: K, value: V): V? {
        val i = indexedKeys.indexOf(key)
        if (i == -1) {
            indexedKeys.add(key)
        }
        return super.put(key, value)
    }

    override fun remove(key: K): V? {
        indexedKeys.remove(key)
        return super.remove(key)
    }

    fun getByIndex(index: Int): V? {
        when {
            index < 0 -> {
                throw IndexOutOfBoundsException("Index is negative")
            }
            index >= super.size -> {
                throw IndexOutOfBoundsException("Index is bigger than the size of map")
            }
            else -> {
                return super.get(indexedKeys[index])
            }
        }
    }

    fun removeByIndex(index: Int): V? {
        when {
            index < 0 -> {
                throw IndexOutOfBoundsException("Index is negative")
            }
            index >= super.size -> {
                throw IndexOutOfBoundsException("Index is bigger than the size of map")
            }
            else -> {
                val key = indexedKeys[index]
                indexedKeys.removeAt(index)
                return super.remove(key)
            }
        }
    }

    fun getIndexOfKey(key: K): Int {
        return indexedKeys.indexOf(key)
    }
}