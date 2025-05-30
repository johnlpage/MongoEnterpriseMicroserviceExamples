String newJson = """
{
  itemId: 1242414,
  title: "2 port USB battery pack 10,000mAh",
  description: "Ultra lightweight.",
  stockCount: 2,
  basePrice: 19.99,
  features: {
     ports: 2,
     capacity: 10000
  }
}
"""

Document newDoc = Document.parse(newJSON);
Document query = Filters.eq("itemId",
        newDoc.get("ItemId"));

inventory.replaceOne( query, newDoc );
