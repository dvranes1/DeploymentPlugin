import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.ui.Messages

object SuggestionUI {

    fun showSuggestion(project: com.intellij.openapi.project.Project, text: String) {
        TransactionGuard.getInstance().submitTransactionAndWait {
            ApplicationManager.getApplication().invokeLater {
                Messages.showDialog(
                    text,
                    "AI Error Fix Suggestion",
                    arrayOf("Close"),
                    0,
                    Messages.getInformationIcon()
                )
            }
        }
    }
}
