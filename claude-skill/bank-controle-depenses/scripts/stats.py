#!/usr/bin/env python3
"""Statistiques exactes d'un export de l'app Bank (format bank-export v1).

Usage : python3 stats.py <bank-export.json> [--mois AAAA-MM] [--json]

Compte comme dépense : une sortie rangée dans une enveloppe qui n'est pas de l'épargne
(remboursements rangés déduits). Ne compte pas : hors budget, virements vers mes comptes,
épargne. Ce qui est à trier est donné à part. Les moyennes portent sur 12 mois glissants au plus
(moins s'il y a moins de mois terminés dans l'export).
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


def month_of(day):
    return day[:7]


def load(path):
    with open(path, encoding="utf-8") as handle:
        data = json.load(handle)
    if data.get("format") != "bank-export":
        sys.exit("Ce fichier n'est pas un export bank-export.")
    return data


def build(data, focus=None):
    envelopes = {e["id"]: e for e in data.get("envelopes", [])}
    ops = data.get("operations", [])
    created = data.get("created") or date.today().isoformat()
    current = month_of(created)
    counted = []
    unsorted = defaultdict(float)
    out_of_budget = defaultdict(float)
    to_accounts = defaultdict(float)
    for op in ops:
        m = month_of(op["date"])
        if op["status"] == "à trier" and op["amount"] < 0:
            unsorted[m] += -op["amount"]
        elif op["status"] == "hors budget" and op["amount"] < 0:
            out_of_budget[m] += -op["amount"]
        elif op["status"] == "vers mes comptes":
            to_accounts[m] += -op["amount"]
        elif op["status"] == "rangée" and op.get("envelope") in envelopes:
            env = envelopes[op["envelope"]]
            if env["family"] == "épargne":
                continue
            counted.append((op, env))

    months = sorted({month_of(o["date"]) for o in ops})
    finished = [m for m in months if m < current][-12:]
    focus = focus or current

    by_month = defaultdict(float)
    by_cat = defaultdict(lambda: defaultdict(float))
    by_env = defaultdict(lambda: defaultdict(float))
    by_family = defaultdict(lambda: defaultdict(float))
    for op, env in counted:
        m = month_of(op["date"])
        spent = -op["amount"]
        by_month[m] += spent
        by_cat[env["category"]][m] += spent
        by_env[env["name"]][m] += spent
        by_family[env["family"]][m] += spent

    def avg(series):
        return round(sum(series.get(m, 0.0) for m in finished) / len(finished), 2) if finished else None

    focus_ops = [(op, env) for op, env in counted if month_of(op["date"]) == focus]
    merchants = defaultdict(lambda: [0, 0.0, ""])
    weekend = weekday = 0.0
    small = [0, 0.0]
    for op, _ in focus_ops:
        spent = -op["amount"]
        key = norm(op["label"])
        merchants[key][0] += 1
        merchants[key][1] += spent
        merchants[key][2] = op["label"]
        if date.fromisoformat(op["date"]).weekday() >= 5:
            weekend += spent
        else:
            weekday += spent
        if spent < 10:
            small[0] += 1
            small[1] += spent

    # Abonnements : même libellé, au moins 3 mois distincts, une fois par mois, montant stable à 10 %.
    outflows = defaultdict(list)
    for op in ops:
        if op["amount"] < 0 and op["status"] != "vers mes comptes":
            outflows[norm(op["label"])].append(op)
    recurring = []
    for key, items in outflows.items():
        per_month = defaultdict(list)
        for op in items:
            per_month[month_of(op["date"])].append(op)
        if len(per_month) < 3 or any(len(v) > 1 for v in per_month.values()):
            continue
        amounts = [-op["amount"] for op in items]
        if max(amounts) - min(amounts) > min(amounts) / 10:
            continue
        last = max(items, key=lambda o: o["date"])
        recurring.append({"label": last["label"], "monthly": -last["amount"], "months": len(per_month), "last": last["date"],
                          "rose": amounts[-1] > amounts[0]})
    recurring.sort(key=lambda r: -r["monthly"])

    budgets = {}
    for env in envelopes.values():
        for period in env.get("periods", []):
            if period["start"][:7] <= focus <= period["end"][:7]:
                budgets[env["name"]] = {"budget": period["budget"], "spent_in_period": period["spent"],
                                        "period": f'{period["start"]} → {period["end"]}', "family": env["family"]}

    return {
        "export_id": data.get("export_id"),
        "focus_month": focus,
        "finished_months_used_for_averages": finished,
        "total_by_month": {m: round(by_month.get(m, 0.0), 2) for m in months},
        "monthly_average": avg(by_month),
        "by_family": {f: {"focus": round(s.get(focus, 0.0), 2), "average": avg(s)} for f, s in by_family.items()},
        "by_category": sorted(({"category": c, "focus": round(s.get(focus, 0.0), 2), "average": avg(s),
                                "by_month": {m: round(v, 2) for m, v in sorted(s.items())}} for c, s in by_cat.items()),
                              key=lambda x: -x["focus"]),
        "by_envelope": sorted(({"envelope": e, "focus": round(s.get(focus, 0.0), 2), "average": avg(s),
                                **({"budget": budgets[e]} if e in budgets else {})} for e, s in by_env.items()),
                              key=lambda x: -x["focus"]),
        "merchants_by_amount": sorted(({"label": v[2], "count": v[0], "total": round(v[1], 2)} for v in merchants.values()),
                                      key=lambda x: -x["total"])[:12],
        "merchants_by_frequency": sorted(({"label": v[2], "count": v[0], "total": round(v[1], 2)} for v in merchants.values() if v[0] > 1),
                                         key=lambda x: -x["count"])[:12],
        "weekend_vs_weekday": {"weekend": round(weekend, 2), "weekday": round(weekday, 2)},
        "small_expenses_under_10": {"count": small[0], "total": round(small[1], 2)},
        "recurring": recurring,
        "recurring_monthly_total": round(sum(r["monthly"] for r in recurring), 2),
        "not_counted": {"to_sort": round(unsorted.get(focus, 0.0), 2), "out_of_budget": round(out_of_budget.get(focus, 0.0), 2),
                        "to_my_accounts": round(to_accounts.get(focus, 0.0), 2)},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("export")
    parser.add_argument("--mois", help="mois analysé (AAAA-MM), par défaut celui de l'export")
    parser.add_argument("--json", action="store_true", help="sortie JSON brute")
    args = parser.parse_args()
    result = build(load(args.export), args.mois)
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=1))
        return
    print(f"Mois analysé : {result['focus_month']} · moyennes sur {len(result['finished_months_used_for_averages'])} mois terminés")
    print(f"Moyenne mensuelle : {result['monthly_average']} €")
    print("\nPar catégorie (mois analysé | moyenne) :")
    for c in result["by_category"]:
        print(f"  {c['category']}: {c['focus']} € | {c['average']} €")
    print("\nPar enveloppe (mois analysé | moyenne | budget de la période) :")
    for e in result["by_envelope"]:
        budget = e.get("budget", {})
        print(f"  {e['envelope']}: {e['focus']} € | {e['average']} € | {budget.get('budget', '–')} € ({budget.get('period', '')})")
    print("\nCommerçants les plus coûteux :")
    for m in result["merchants_by_amount"]:
        print(f"  {m['label']}: {m['total']} € ({m['count']}×)")
    print(f"\nWeek-ends : {result['weekend_vs_weekday']['weekend']} € · semaine : {result['weekend_vs_weekday']['weekday']} €")
    print(f"Petites dépenses (< 10 €) : {result['small_expenses_under_10']['count']} pour {result['small_expenses_under_10']['total']} €")
    print(f"\nAbonnements et prélèvements réguliers : {result['recurring_monthly_total']} € par mois")
    for r in result["recurring"]:
        print(f"  {r['label']}: {r['monthly']} € (vu {r['months']} mois{', en hausse' if r['rose'] else ''})")
    nc = result["not_counted"]
    print(f"\nNon compté : à trier {nc['to_sort']} € · hors budget {nc['out_of_budget']} € · vers mes comptes {nc['to_my_accounts']} €")


if __name__ == "__main__":
    main()
