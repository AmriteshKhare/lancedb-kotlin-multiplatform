#!/usr/bin/env python3
"""Generate recipes_seed.json with 384-dim MiniLM embeddings for the Android demo."""

from __future__ import annotations

import json
import shutil
from pathlib import Path

RECIPES = [
    {
        "id": "1",
        "title": "Spicy Shakshuka",
        "description": "Eggs poached in a spicy tomato and pepper sauce with cumin.",
        "ingredients": "eggs, tomatoes, chili, cumin, onion, garlic",
    },
    {
        "id": "2",
        "title": "Classic Pancakes",
        "description": "Fluffy buttermilk pancakes served with maple syrup.",
        "ingredients": "flour, eggs, milk, butter, maple syrup",
    },
    {
        "id": "3",
        "title": "Avocado Toast",
        "description": "Smashed avocado on sourdough with lemon and chili flakes.",
        "ingredients": "avocado, sourdough, lemon, chili, olive oil",
    },
    {
        "id": "4",
        "title": "Green Smoothie Bowl",
        "description": "Spinach and banana smoothie topped with granola.",
        "ingredients": "spinach, banana, yogurt, granola, honey",
    },
    {
        "id": "5",
        "title": "Huevos Rancheros",
        "description": "Fried eggs on tortillas with salsa and black beans.",
        "ingredients": "eggs, tortillas, salsa, beans, cilantro",
    },
    {
        "id": "6",
        "title": "Overnight Oats",
        "description": "Creamy oats soaked overnight with berries.",
        "ingredients": "oats, milk, chia, blueberries, honey",
    },
    {
        "id": "7",
        "title": "French Croissant",
        "description": "Buttery laminated pastry baked until golden.",
        "ingredients": "flour, butter, yeast, sugar, salt",
    },
    {
        "id": "8",
        "title": "Spicy Egg Tacos",
        "description": "Scrambled eggs with hot sauce in corn tortillas.",
        "ingredients": "eggs, tortillas, hot sauce, cheese, cilantro",
    },
    {
        "id": "9",
        "title": "Miso Soup Breakfast",
        "description": "Light miso broth with tofu and scallions.",
        "ingredients": "miso, tofu, scallions, seaweed, dashi",
    },
    {
        "id": "10",
        "title": "Berry Parfait",
        "description": "Layers of yogurt, granola, and mixed berries.",
        "ingredients": "yogurt, granola, strawberries, blueberries",
    },
    {
        "id": "11",
        "title": "Vegetarian Buddha Bowl",
        "description": "Roasted vegetables with quinoa and tahini dressing.",
        "ingredients": "quinoa, chickpeas, sweet potato, tahini, kale",
    },
    {
        "id": "12",
        "title": "Caprese Salad",
        "description": "Tomato, mozzarella, and basil with balsamic glaze.",
        "ingredients": "tomato, mozzarella, basil, balsamic, olive oil",
    },
    {
        "id": "13",
        "title": "Lentil Soup",
        "description": "Hearty red lentils simmered with carrots and spices.",
        "ingredients": "lentils, carrots, onion, cumin, vegetable broth",
    },
    {
        "id": "14",
        "title": "Margherita Pizza",
        "description": "Thin crust pizza with tomato, mozzarella, and basil.",
        "ingredients": "dough, tomato sauce, mozzarella, basil, olive oil",
    },
    {
        "id": "15",
        "title": "Grilled Salmon",
        "description": "Salmon fillet with lemon dill sauce and asparagus.",
        "ingredients": "salmon, lemon, dill, asparagus, butter",
    },
    {
        "id": "16",
        "title": "Chicken Tikka Masala",
        "description": "Creamy tomato curry with marinated chicken.",
        "ingredients": "chicken, yogurt, tomato, garam masala, cream",
    },
    {
        "id": "17",
        "title": "Beef Stir Fry",
        "description": "Quick wok-fried beef with broccoli and soy sauce.",
        "ingredients": "beef, broccoli, soy sauce, garlic, ginger",
    },
    {
        "id": "18",
        "title": "Mushroom Risotto",
        "description": "Creamy arborio rice with wild mushrooms and parmesan.",
        "ingredients": "arborio rice, mushrooms, parmesan, white wine, broth",
    },
    {
        "id": "19",
        "title": "Thai Green Curry",
        "description": "Coconut curry with vegetables and green curry paste.",
        "ingredients": "coconut milk, green curry paste, eggplant, basil, rice",
    },
    {
        "id": "20",
        "title": "Chocolate Lava Cake",
        "description": "Warm chocolate cake with a molten center.",
        "ingredients": "dark chocolate, butter, eggs, sugar, flour",
    },
    {
        "id": "21",
        "title": "Tiramisu",
        "description": "Espresso-soaked ladyfingers with mascarpone cream.",
        "ingredients": "mascarpone, espresso, ladyfingers, cocoa, eggs",
    },
    {
        "id": "22",
        "title": "Mango Sticky Rice",
        "description": "Sweet coconut rice served with ripe mango.",
        "ingredients": "sticky rice, coconut milk, mango, sugar, salt",
    },
    {
        "id": "23",
        "title": "Chia Pudding",
        "description": "Vanilla chia pudding topped with passion fruit.",
        "ingredients": "chia seeds, almond milk, vanilla, passion fruit",
    },
    {
        "id": "24",
        "title": "Garlic Noodles",
        "description": "Simple noodles tossed with browned garlic and butter.",
        "ingredients": "noodles, garlic, butter, parmesan, parsley",
    },
    {
        "id": "25",
        "title": "Kimchi Fried Rice",
        "description": "Fried rice with kimchi, egg, and sesame oil.",
        "ingredients": "rice, kimchi, egg, sesame oil, scallions",
    },
]

ROOT = Path(__file__).resolve().parents[2]
OUT_JSON = ROOT / "androidApp/src/androidMain/assets/recipes_seed.json"
MODELS_DIR = ROOT / "androidApp/src/androidMain/assets/models"
FASTEMBED_CACHE = Path.home() / ".cache" / "fastembed" / "models--Qdrant--all-MiniLM-L6-v2-onnx"


def main() -> None:
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError as exc:
        raise SystemExit(
            "Install sentence-transformers: pip install sentence-transformers"
        ) from exc

    model = SentenceTransformer("all-MiniLM-L6-v2")
    texts = [
        f"passage: {r['title']}. {r['description']}. Ingredients: {r['ingredients']}"
        for r in RECIPES
    ]
    vectors = model.encode(texts, normalize_embeddings=True)

    payload = []
    for recipe, vector in zip(RECIPES, vectors):
        payload.append(
            {
                **recipe,
                "vector": [float(x) for x in vector.tolist()],
            }
        )

    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(f"Wrote {OUT_JSON} ({len(payload)} recipes)")

    if FASTEMBED_CACHE.exists():
        MODELS_DIR.mkdir(parents=True, exist_ok=True)
        for name in [
            "model.onnx",
            "tokenizer.json",
            "config.json",
            "special_tokens_map.json",
            "tokenizer_config.json",
        ]:
            src = FASTEMBED_CACHE / name
            if src.exists():
                shutil.copy2(src, MODELS_DIR / name)
                print(f"Copied {name} -> {MODELS_DIR}")
    else:
        print(
            "Run the app once online (or `python -c \"from fastembed import TextEmbedding; TextEmbedding()\"`) "
            "then re-run this script to bundle ONNX models into assets/models/"
        )


if __name__ == "__main__":
    main()
