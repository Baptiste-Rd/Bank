package fr.baptiste.bank.domain.model

/** Rôle d'un compte bancaire dans l'app. Seul le compte courant porte des dépenses budgétées. */
enum class AccountType { CURRENT, SAVINGS, INVESTMENT }

/** Origine d'une transaction. */
enum class TransactionSource {
    /** Import d'un export CSV de la banque. */
    CSV,

    /** Synchronisation automatique via un agrégateur bancaire (phase 2). */
    API,

    /** Saisie à la main (espèces, dépense pas encore remontée par la banque). */
    MANUAL,
}

/** Façon dont une imputation a été décidée. */
enum class ImputationMode {
    /** Appliquée automatiquement par une règle de catégorisation. */
    RULE,

    /** Choisie ou corrigée par l'utilisateur. */
    MANUAL,
}

/** Façon dont une règle compare son texte au libellé d'une transaction. */
enum class RuleMatchType { CONTAINS, STARTS_WITH }
