#!/usr/bin/env python3
"""Vérifications exactes d'un export de l'app Bank (format bank-export v1).

Usage : python3 verifier.py <bank-export.json> [--compte acc_…] [--du AAAA-MM-JJ] [--au AAAA-MM-JJ] [--json]

Repère : doublons probables (même montant, même commerçant, à un jour près), dates bancaires
éloignées de l'achat (plus de 45 jours, ou inscription avant l'achat), dépenses rangées avant le
départ d'une enveloppe ou après son arrêt, libellés rangés dans plusieurs enveloppes différentes,
opérations à trier. Chaque constat porte les identifiants op_… à reprendre dans le plan.
"""
import argparse
import json
import re
import sys
import unicodedata
from collections import defaultdict
from datetime import date


def norm(text):
    text = unicodedata.normalize("NFD", text or "")
    text = "".join(c for c in text if unicodedata.category(c) != "Mn").upper()
    text = re.sub(r"\b(PAIEMENT|CB|CARTE|PRLV|SEPA|VIR|FACTURE|ACHAT)\b", " ", text)
    text = re.sub(r"\d+", " ", text)
    return re.sub(r"\s+", " ", text).strip() or "?"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("export")
    parser.add_argument("--compte")
    parser.add_argument("--du")
    parser.add_argument("--au")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    with open(args.export, encoding="utf-8") as handle:
        data = json.load(handle)
    if data.get("format") != "bank-export":
        sys.exit("Ce fichier n'est pas un export bank-export.")
    envelopes = {e["id"]: e for e in data.get("envelopes", [])}
    ops = [o for o in data.get("operations", [])
           if (not args.compte or o.get("account") == args.compte)
           and (not args.du or o["date"] >= args.du)
           and (not args.au or o["date"] <= args.au)]

    findings = []

    # Doublons : même montant et même commerçant, à un jour près.
    by_key = defaultdict(list)
    for op in ops:
        by_key[(norm(op["label"]), op["amount"])].append(op)
    for (label, amount), items in by_key.items():
        items.sort(key=lambda o: o["date"])
        for a, b in zip(items, items[1:]):
            gap = abs((date.fromisoformat(b["date"]) - date.fromisoformat(a["date"])).days)
            if gap <= 1:
                findings.append({"kind": "doublon probable", "operations": [a["id"], b["id"]],
                                 "detail": f'{a["label"]} {amount} € le {a["date"]} et le {b["date"]}'})

    # Dates : inscription avant l'achat, ou plus de 45 jours après.
    for op in ops:
        booked = op.get("booking_date")
        if not booked:
            continue
        gap = (date.fromisoformat(booked) - date.fromisoformat(op["date"])).days
        if gap < 0 or gap > 45:
            findings.append({"kind": "date suspecte", "operations": [op["id"]],
                             "detail": f'{op["label"]} : achat le {op["date"]}, inscrit le {booked} ({gap} jours)'})

    # Enveloppe : dépense avant son départ ou après son arrêt.
    for op in ops:
        env = envelopes.get(op.get("envelope"))
        if not env:
            continue
        if env.get("start") and op["date"] < env["start"]:
            findings.append({"kind": "avant le départ de l'enveloppe", "operations": [op["id"]],
                             "detail": f'{op["label"]} du {op["date"]} dans {env["name"]} (départ {env["start"]}) : rangée mais pas comptée dans son reste'})
        stopped = env.get("stopped")
        if stopped and stopped != "oui" and op["date"] > stopped:
            findings.append({"kind": "après l'arrêt de l'enveloppe", "operations": [op["id"]],
                             "detail": f'{op["label"]} du {op["date"]} dans {env["name"]} (arrêtée le {stopped})'})

    # Même libellé rangé dans plusieurs enveloppes : à vérifier.
    places = defaultdict(lambda: defaultdict(list))
    for op in ops:
        if op.get("envelope") in envelopes:
            places[norm(op["label"])][op["envelope"]].append(op["id"])
    for label, where in places.items():
        if len(where) > 1:
            names = ", ".join(f'{envelopes[e]["name"]} ({len(ids)})' for e, ids in where.items())
            findings.append({"kind": "rangement incohérent", "operations": [i for ids in where.values() for i in ids],
                             "detail": f"« {label} » rangé dans : {names}"})

    to_sort = [o for o in ops if o["status"] == "à trier"]
    result = {
        "scope": {"account": args.compte, "from": args.du, "to": args.au, "operations": len(ops)},
        "to_sort": {"count": len(to_sort), "total": round(-sum(o["amount"] for o in to_sort if o["amount"] < 0), 2),
                    "operations": [o["id"] for o in to_sort]},
        "findings": findings,
    }
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=1))
        return
    print(f"{len(ops)} opérations vérifiées · {len(to_sort)} à trier ({result['to_sort']['total']} €)")
    if not findings:
        print("Aucune anomalie repérée.")
    for f in findings:
        print(f"- {f['kind']} : {f['detail']} [{', '.join(f['operations'][:6])}{'…' if len(f['operations']) > 6 else ''}]")


if __name__ == "__main__":
    main()
