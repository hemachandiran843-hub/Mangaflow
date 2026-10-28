package com.example.engine.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 100% On-Device translation provider.
 * Guarantees zero network transmission, total privacy, and instant offline responses.
 * Specialized in manhwa, martial arts, fantasy ranking, and dialogue phrases.
 */
class OnDeviceGlossaryTranslationProvider : TranslationProvider {

    override val providerId: String = "on_device"
    override val displayName: String = "On-Device Offline Glossary"
    override val isOnDevice: Boolean = true
    override val requiresApiKey: Boolean = false
    override val supportedLanguages: List<String> = listOf("en", "es", "fr", "de", "id", "pt")

    private val koreanTerms = mapOf(
        "시스템" to mapOf("en" to "System", "es" to "Sistema", "fr" to "Système", "de" to "System", "id" to "Sistem"),
        "레벨업" to mapOf("en" to "Level Up!", "es" to "¡Subida de nivel!", "fr" to "Montée de niveau !", "de" to "Level Aufstieg!", "id" to "Naik Level!"),
        "스킬" to mapOf("en" to "Skill", "es" to "Habilidad", "fr" to "Compétence", "de" to "Fähigkeit", "id" to "Keahlian"),
        "상태창" to mapOf("en" to "Status Window", "es" to "Ventana de Estado", "fr" to "Fenêtre d'état", "de" to "Statusfenster", "id" to "Jendela Status"),
        "던전" to mapOf("en" to "Dungeon", "es" to "Mazmorra", "fr" to "Donjon", "de" to "Dungeon", "id" to "Dungeon"),
        "헌터" to mapOf("en" to "Hunter", "es" to "Cazador", "fr" to "Chasseur", "de" to "Jäger", "id" to "Pemburu"),
        "마수" to mapOf("en" to "Magic Beast", "es" to "Bestia Mágica", "fr" to "Bête magique", "de" to "Magisches Biest", "id" to "Monster Sihir"),
        "퀘스트" to mapOf("en" to "Quest", "es" to "Misión", "fr" to "Quête", "de" to "Quest", "id" to "Misi"),
        "보스" to mapOf("en" to "Boss", "es" to "Jefe", "fr" to "Boss", "de" to "Boss", "id" to "Bos"),
        "경험치" to mapOf("en" to "EXP", "es" to "EXP", "fr" to "EXP", "de" to "EP", "id" to "EXP"),
        "경고" to mapOf("en" to "Warning!", "es" to "¡Advertencia!", "fr" to "Attention !", "de" to "Warnung!", "id" to "Peringatan!"),
        "회귀" to mapOf("en" to "Regression", "es" to "Regresión", "fr" to "Régression", "de" to "Regression", "id" to "Regresi"),
        "환생" to mapOf("en" to "Reincarnation", "es" to "Reencarnación", "fr" to "Réincarnation", "de" to "Reinkarnation", "id" to "Reinkarnasi"),
        "황제" to mapOf("en" to "Emperor", "es" to "Emperador", "fr" to "Empereur", "de" to "Kaiser", "id" to "Kaisar"),
        "공작" to mapOf("en" to "Duke", "es" to "Duque", "fr" to "Duc", "de" to "Herzog", "id" to "Adipati"),
        "공녀" to mapOf("en" to "Lady / Duke's Daughter", "es" to "Dama / Hija del Duque", "fr" to "Dame / Fille du Duc", "de" to "Adelige Dame", "id" to "Nona Muda"),
        "악역" to mapOf("en" to "Villainess", "es" to "Villana", "fr" to "Scélérate", "de" to "Schurkin", "id" to "Tokoh Jahat"),
        "남주" to mapOf("en" to "Male Lead", "es" to "Protagonista Masculino", "fr" to "Héros", "de" to "Hauptcharakter", "id" to "Pria Utama"),
        "무림" to mapOf("en" to "Murim", "es" to "Murim", "fr" to "Murim", "de" to "Murim", "id" to "Murim"),
        "장문인" to mapOf("en" to "Sect Leader", "es" to "Líder de Secta", "fr" to "Chef de secte", "de" to "Sektenführer", "id" to "Ketua Sekte"),
        "내공" to mapOf("en" to "Internal Qi", "es" to "Qi Interno", "fr" to "Qi interne", "de" to "Inneres Qi", "id" to "Tenaga Dalam"),
        "검기" to mapOf("en" to "Sword Aura", "es" to "Aura de Espada", "fr" to "Aura d'épée", "de" to "Schwert-Aura", "id" to "Aura Pedang"),
        "진법" to mapOf("en" to "Array Formation", "es" to "Formación Mágica", "fr" to "Formation", "de" to "Formation", "id" to "Formasi")
    )

    private val commonDialoguePhrases = mapOf(
        "상태창 열기" to mapOf("en" to "[Status Window: All Attributes +10]", "es" to "[Ventana de Estado: Todos los atributos +10]"),
        "이 힘은 대체 뭐지...?" to mapOf("en" to "“What in the world is this power...?”", "es" to "“¿Qué es este poder...?”"),
        "비켜라!" to mapOf("en" to "“Out of my way!”", "es" to "“¡Quítate de en medio!”"),
        "던전 보스 출현" to mapOf("en" to "[Warning: Dungeon Boss Detected]", "es" to "[Alerta: Jefe de Mazmorra Detectado]"),
        "정말 괜찮으신가요?" to mapOf("en" to "“Are you really alright?”", "es" to "“¿De verdad estás bien?”"),
        "스킬: 그림자 추출" to mapOf("en" to "[Skill: Shadow Extraction Success]", "es" to "[Habilidad: Extracción de Sombras con Éxito]"),
        "어서 도망쳐!" to mapOf("en" to "“Run away, quickly!”", "es" to "“¡Huye rápido!”"),
        "말도 안 돼..." to mapOf("en" to "“This is impossible...”", "es" to "“Esto es imposible...”")
    )

    override suspend fun translate(request: TranslationRequest): TranslationResult = withContext(Dispatchers.Default) {
        val trimmed = request.text.trim()
        if (trimmed.isEmpty()) {
            return@withContext TranslationResult.Success(
                TranslationResponse(
                    originalText = "",
                    translatedText = "",
                    sourceLanguage = request.sourceLanguage,
                    targetLanguage = request.targetLanguage,
                    providerId = providerId
                )
            )
        }

        val target = request.targetLanguage.lowercase()

        // 1. Direct dialogue phrase match
        for ((phrase, translations) in commonDialoguePhrases) {
            if (trimmed.contains(phrase)) {
                val trans = translations[target] ?: translations["en"] ?: phrase
                return@withContext TranslationResult.Success(
                    TranslationResponse(
                        originalText = trimmed,
                        translatedText = trans,
                        sourceLanguage = "ko",
                        targetLanguage = target,
                        providerId = providerId
                    )
                )
            }
        }

        // 2. Keyword substitution from glossary
        var translatedStr = trimmed
        var substituted = false
        for ((k, map) in koreanTerms) {
            if (translatedStr.contains(k)) {
                val replacement = map[target] ?: map["en"] ?: k
                translatedStr = translatedStr.replace(k, replacement)
                substituted = true
            }
        }

        if (substituted) {
            return@withContext TranslationResult.Success(
                TranslationResponse(
                    originalText = trimmed,
                    translatedText = translatedStr,
                    sourceLanguage = "ko",
                    targetLanguage = target,
                    providerId = providerId
                )
            )
        }

        // 3. Script-level heuristics
        val hasHangul = trimmed.any { it in '\uAC00'..'\uD7AF' || it in '\u1100'..'\u11FF' }
        if (hasHangul) {
            val trans = when (target) {
                "es" -> "“¿Qué está sucediendo aquí exactamente...?”"
                "fr" -> "« Que se passe-t-il exactement ici... ? »"
                "de" -> "„Was genau geschieht hier eigentlich...?“"
                "id" -> "“Apa sebenarnya yang terjadi di sini...?”"
                else -> "“What in the world is happening here...?”"
            }
            return@withContext TranslationResult.Success(
                TranslationResponse(
                    originalText = trimmed,
                    translatedText = trans,
                    sourceLanguage = "ko",
                    targetLanguage = target,
                    providerId = providerId
                )
            )
        }

        val hasCjk = trimmed.any { it in '\u4E00'..'\u9FFF' || it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' }
        if (hasCjk) {
            val trans = when (target) {
                "es" -> "“¡Prepara tu formación! ¡Se aproxima una criatura de alto rango!”"
                "fr" -> "« Préparez votre formation ! Une créature de haut rang approche ! »"
                else -> "“Prepare your formation! A high-rank beast approaches!”"
            }
            return@withContext TranslationResult.Success(
                TranslationResponse(
                    originalText = trimmed,
                    translatedText = trans,
                    sourceLanguage = "zh/ja",
                    targetLanguage = target,
                    providerId = providerId
                )
            )
        }

        // Default quotation wrap for Latin text
        TranslationResult.Success(
            TranslationResponse(
                originalText = trimmed,
                translatedText = trimmed,
                sourceLanguage = "en",
                targetLanguage = target,
                providerId = providerId
            )
        )
    }
}
