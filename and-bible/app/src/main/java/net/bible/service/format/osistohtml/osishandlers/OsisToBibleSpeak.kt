package net.bible.service.format.osistohtml.osishandlers

import android.speech.tts.TextToSpeech
import net.bible.service.format.osistohtml.taghandler.DivHandler
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes

import java.util.Stack

abstract class SpeakCommand {
    open fun copy(): SpeakCommand {
        return this
    }
}

class TextCommand(text: String) : SpeakCommand() {
    var text: String = text.trim()
        set(value) {
            field = value.trim()
        }

    override fun toString(): String {
        return text;
    }

    override fun copy(): SpeakCommand {
        return TextCommand(text)
    }
}

class TitleCommand(val text: String): SpeakCommand() {
    override fun toString(): String {
        return text;
    }
}

class ParagraphChange : SpeakCommand()

class SpeakCommands: ArrayList<SpeakCommand>() {

    fun copy(): SpeakCommands {
        val cmds = SpeakCommands()
        for(cmd in this) {
            cmds.add(cmd.copy())
        }
        return cmds
    }

    override fun add(index: Int, element: SpeakCommand) {
        if(element is TextCommand) {
            val currentCmd =  this[index]
            if (currentCmd is TextCommand) {
                val newText = "${element.text} ${currentCmd.text}"
                if (newText.length > TextToSpeech.getMaxSpeechInputLength())
                    return super.add(index, element)
                else {
                    currentCmd.text = newText
                    return
                }
            }
            else {
                return super.add(index, element)
            }
        }
        else {
            return super.add(index, element)
        }
    }

    override fun add(element: SpeakCommand): Boolean {
        if(element is TextCommand) {
            val lastCommand = try {this.last()} catch (e: NoSuchElementException) {null}
            if(lastCommand is TextCommand) {
                val newText = "${lastCommand.text} ${element.text}"
                if (newText.length > TextToSpeech.getMaxSpeechInputLength())
                    return super.add(element)
                else {
                    lastCommand.text = newText
                    return true
                }
            }
            else {
                if(!element.text.isEmpty()) {
                    return super.add(element)
                }
                else {
                    return false;
                }
            }
        }
        else {
            return super.add(element)
        }
    }

    override fun toString(): String {
        return this.joinToString(" ") { it.toString() }
    }

    override fun addAll(elements: Collection<SpeakCommand>): Boolean {
        if(elements is SpeakCommands) {
            for(e in elements) {
                add(e)
            }
            return true
        }
        else {
            return super.addAll(elements)
        }
    }
}

class OsisToBibleSpeak : OsisSaxHandler() {
    val speakCommands = SpeakCommands()

    private var anyTextWritten = false

    private enum class TAG_TYPE {NORMAL, TITLE, PARAGRPAH}

    private data class StackEntry(val visible: Boolean, val tagType: TAG_TYPE=TAG_TYPE.NORMAL)

    private val elementStack = Stack<StackEntry>()

    override fun startDocument() {
        reset()
        elementStack.push(StackEntry(true))
    }

    override fun endDocument() {
        elementStack.pop()
    }

    override fun startElement(namespaceURI: String,
                              sName: String, // simple name
                              qName: String, // qualified name
                              attrs: Attributes?) {
        val name = getName(sName, qName) // element name

        val peekVisible = elementStack.peek().visible

        if (name == OSISUtil.OSIS_ELEMENT_VERSE) {
            anyTextWritten = false
            elementStack.push(StackEntry(true))
        } else if (name == OSISUtil.OSIS_ELEMENT_NOTE) {
            elementStack.push(StackEntry(false))
        } else if (name == OSISUtil.OSIS_ELEMENT_TITLE) {
            elementStack.push(StackEntry(peekVisible, TAG_TYPE.TITLE))
        } else if (name == OSISUtil.OSIS_ELEMENT_DIV) {
            val type = attrs?.getValue("type") ?: ""
            val isVerseBeginning = attrs?.getValue("sID") != null
            val isParagraphType = DivHandler.PARAGRAPH_TYPE_LIST.contains(type)
            if(isParagraphType && !isVerseBeginning) {
                speakCommands.add(ParagraphChange())
                elementStack.push(StackEntry(peekVisible, TAG_TYPE.PARAGRPAH))
            }
            else {
                elementStack.push(StackEntry(peekVisible))
            }
        } else if (name == OSISUtil.OSIS_ELEMENT_REFERENCE) {
            elementStack.push(StackEntry(peekVisible))
        } else if (name == OSISUtil.OSIS_ELEMENT_L
                || name == OSISUtil.OSIS_ELEMENT_LB ||
                name == OSISUtil.OSIS_ELEMENT_P) {
            if(anyTextWritten) {
                speakCommands.add(ParagraphChange())
            }
            elementStack.push(StackEntry(peekVisible, TAG_TYPE.PARAGRPAH))
        } else {
            elementStack.push(StackEntry(peekVisible))
        }
    }

    override fun endElement(namespaceURI: String,
                            simplifiedName: String,
                            qualifiedName: String
    ) {
        val name = getName(simplifiedName, qualifiedName)
        val state = elementStack.pop()

        if(state.tagType == TAG_TYPE.PARAGRPAH) {
            if(anyTextWritten) {
                anyTextWritten = false;
            }
        }
    }

    /*
     * Handle characters encountered in tags
    */
    override fun characters(buf: CharArray, offset: Int, len: Int) {
        val currentState = elementStack.peek()
        val s = String(buf, offset, len)
        if(currentState.visible) {
            if(currentState.tagType == TAG_TYPE.TITLE) {
                speakCommands.add(TitleCommand(s))
            }
            else {
                speakCommands.add(TextCommand(s))
            }
        }
    }
}

