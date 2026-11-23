**Pizza-Driven Development**

**Scenario**

A pizza restaurant wants an order builder. A customer assembles an order, watches the total update as they change it, and sends it to the restaurant's order service. After that, they can check on the order and, until the kitchen starts it, change or cancel it.

**Pricing rules**

These are the restaurant's rules. Implement them as written.

| Item | Price |
| :---- | :---- |
| Small pizza | \$5 |
| Medium pizza | \$7 |
| Large pizza | \$12 |
| Basic topping | 8% of the base pizza price |
| Premium topping | 11% of the base pizza price |
| Tax | 6.5% of the total order |

* **Basic toppings:** pepperoni, sausage, extra cheese, onions, black olives, green peppers  
* **Premium toppings:** bacon, chicken, garlic, artichoke hearts, sun-dried tomatoes, pineapple  
* **Tip:** the customer enters a tip either as a dollar amount or as a percentage of the total bill.  
* **Promo codes:**  
  * ILOVEPIZZA gives a 10% discount on the total order before tax.  
  * SAVE5 takes \$5 off the order if the order total before tax is over \$20.  
  * SAVE5 cannot be combined with ILOVEPIZZA.

All amounts shown to the customer are in dollars and whole cents.

**Requirements**

**1\. Order builder** An order contains one or more line items. Each line item has a size, a quantity, and zero or more toppings. The customer can add, remove, and modify line items and toppings.

**2\. Running total** The total updates automatically whenever the customer adds, removes, or otherwise modifies the order, including changes to the tip or the promo code. The total the customer sees while building the order must match the total the restaurant charges for that order.

**3\. Order description** On request, the program produces a written description of the order including quantity, size, and the list of toppings for each line item.

**4\. Tip and promo code** Support both tip modes (dollars and percentage) and both promo codes. Invalid input must be handled with a clear message to the customer.

**5\. Order service and lifecycle** When the order is complete, the customer can send it to a remote order service. The order service is part of this exercise: build a small service that runs as a separate process from the order builder and communicates with it over the network. Through the service, the customer can:

* send the order and receive an order ID,  
* query the order's status: Not Started, Started, Ready for Pickup, or Delivered,  
* update the order, or cancel it, but only while it is Not Started.

The restaurant side advances order status. Simulate this using a rest endpoint and document it. Customers use unreliable mobile connections and may see timeouts and retry.

**6\. Extensibility** Adding a new size, topping, topping category, or promo code should not require changing the core pricing logic. Explain in your write-up how your design achieves this.

**Deliverables**

1. Use GitHub, GitLab, BitBucket or some other git repository host to host your code. Do not send us a zip/tar bundle of your project.  
2. A README.md containing:  
   * How to run both the order builder and the order service locally, and how to run the tests (one or two commands each; docker compose up or a seed script is a plus).  
   * A short description of the order service's API (endpoints or messages, with example requests).  
   * **Assumptions:** every place the requirements were ambiguous and the decision you made. We read this section closely.  
   * **Design write-up (\~1 page)** answering:  
     * How do you represent money and calculate the order total? Where is pricing logic located, and does it run in the order builder, the order service, or both? Why?  
     * How does your design handle adding a new size, topping category, or promo code, including a promo that has its own conditions and conflicts with other promos?  
     * How does your order lifecycle work? Walk through a customer retrying a submission after a timeout, and a customer trying to cancel at the moment the kitchen starts the order. What guarantees do you make, and how does your implementation provide them?  
     * What did you deliberately cut or fake to stay in the timebox, and what would you build next?  
   * **AI usage note (a few sentences):** which tools you used and for what, one example of AI output you rejected or had to fix, and one behavior or invariant you verified yourself rather than trusting the AI, and how.  
3. Automated tests for the invariants you consider most important. In the README, say which invariants you chose to test and why, and which you did not have time to cover.

**Constraints & guidance**

* Any language/framework. Any storage (in-memory, SQLite, etc. — no hosted DB required).  
* The order builder can be a web UI, a command-line program, or a desktop/mobile UI. We do not evaluate visual design.  
* No auth required. Assume one restaurant and anonymous customers.  
* Don't build: authentication or customer accounts, real payments, real kitchen or delivery integration, a menu administration UI, multiple locations.  
* Prioritize: correct pricing and a defensible order lifecycle over breadth of features.  
* If you run out of time, ship what works and say so in the README. An honest cut list beats a broken feature.

**What we evaluate**

| Area | What we look for |
| :---- | :---- |
| Data modeling | Menu, order, line item, and order status as distinct concepts; pricing derived from data rather than scattered constants |
| Pricing | Exact arithmetic, consistent rounding, one source of truth for the total |
| Service design | Clear API, server-side validation, safe handling of retries, explicit order state transitions |
| Correctness | Edge cases anticipated without being told; assumptions documented and implemented consistently |
| Testing | Choice of invariants to test; tests that target those invariants rather than only the happy path |
| Extensibility | New menu items and promo codes added without touching core logic |
| Judgment | Scope cuts, library choices, commit history, proportionate design for a 3-hour exercise |
| Communication | README clarity, quality of the assumptions list and the design write-up |
| AI leverage | Effective use of AI without accepting broken or bloated output; evidence the candidate verified what the AI produced |

We do **not** evaluate: CSS polish, deployment.

