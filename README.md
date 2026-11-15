# AI Grocery Companion Agent

**EECS3311 — Software Design Project**

AI Grocery Companion is a Java application that helps users generate grocery shopping plans using natural-language requests. It integrates Anthropic Claude to identify required products, retrieve product information, and assist with shopping decisions.

The application provides both a graphical user interface (GUI) and a command-line interface (CLI).

## 1. Features

- Natural-language shopping requests and AI-generated shopping plans
- Product searching with prices, stores, and source information
- Shopping optimization: Cheapest, Fewest Stores, and One Store
- Alternative product selection
- Interactive shopping checklists
- Saved checklists and recent product searches
- User preference management
- Persistent storage using JSON files

## 2. Prerequisites

Before running the application, install:
- Java Development Kit (JDK) 25
- Apache Maven 3.9 or newer
- Git
- An Anthropic API key with available credits
- An internet connection for AI requests and product searching

The GUI uses JavaFX, which is managed through Maven dependencies.

Verify the installations:

```bash
java -version
mvn -version
git --version
```

## 3. Installation and Dependencies

Clone the project repository:

```bash
git clone https://github.com/ccarjhen/EECS3311Project.git
cd EECS3311Project/eecs3311project
```
Install the dependencies and compile the project:

```bash
mvn clean compile
```

To run the automated tests:

```bash
mvn test
```

## 4. Local API Configuration

The application requires an Anthropic API key to access Claude for shopping-plan generation and online product searches.


From the Maven project directory, copy the example configuration:

```bash
cp .env.example .env
```

Open `.env` in a text editor and replace the placeholder with your personal Anthropic API key:

```dotenv
ANTHROPIC_API_KEY=YOUR_ACTUAL_API_KEY
ANTHROPIC_MODEL=claude-haiku-5-5
```

Replace `YOUR_ACTUAL_API_KEY` locally.

Before launching the application on macOS or Linux, load the environment variables into the terminal session:

```bash
set -a
source .env
set +a
```

The current Java implementation reads these values through `System.getenv()`. It does not automatically read the `.env` file, which is why the environment variables must be loaded first.

The `.env` file is excluded from Git tracking through `.gitignore`.

## 5. Launching the GUI

From the `eecs3311project` directory, run:

```bash
mvn javafx:run
```

The JavaFX application opens with five main views:

1. Home
2. Shopping Plan
3. Product Search
4. Checklist
5. User Preferences

## 6. Launching the CLI

From the same project directory, run:

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.6.2:java -Dexec.mainClass="grocery.cli.ShoppingCLI"
```

The CLI displays the following main menu:

```text
========== AI GROCERY COMPANION ==========
1. Home - Generate Shopping Plan
2. Shopping Plan
3. Products
4. Preferences
5. Checklists
0. Exit
```

Select an option by entering its corresponding number.

## 7. Example Usage

### Example 1 — Generate a Shopping Plan

1. Open the GUI or CLI.
2. Navigate to Home.
3. Enter a natural-language shopping request, for example:

   "I need ingredients to make spaghetti for four people with a budget of $40."

4. Enter the shopping budget when prompted.
5. Generate the shopping plan.

The application uses Claude to identify required items and retrieve available product listings. The resulting plan displays products, quantities, stores, prices, and estimated totals.

### Example 2 — Optimize a Shopping Plan

After generating a plan, navigate to Shopping Plan and choose one of the following:

- **Cheapest:** Prioritizes lower estimated costs.
- **Fewest Stores:** Minimizes the number of stores needed.
- **One Store:** Attempts to purchase the required items from a selected retailer.

The displayed plan updates according to the selected optimization strategy.

### Example 3 — Manage a Checklist

1. Open the Checklist view.
2. Mark items as purchased.
3. Add or remove items or modify quantities.
4. Save the checklist using a name.
5. Reopen the saved checklist later.

### Example 4 — Search for Products

1. Open Product Search.
2. Enter a product name, such as "Milk."
3. Review available product listings and source information.
4. Sort the results or add a selected product to the shopping plan.
5. Revisit recent searches when needed.

### Example 5 — Manage Preferences

Open User Preferences to configure preferred stores, preferred brands, dietary restrictions, allergies, or excluded items.

Saved preferences are considered during subsequent shopping-plan generation. AI-generated recommendations should not be treated as verified allergy-safety information.

## 8. Persistent Data

The application saves local data in the user's home directory:

```text
~/.ai-grocery-companion/
├── checklists.json
├── product-search-history.json
└── preferences.json
```

## 9. Troubleshooting

**Missing Anthropic API key**

Confirm that `.env` contains the correct key and that the environment variables were loaded before running Maven.

**Maven cannot find pom.xml**

Make sure the terminal is in the `eecs3311project` directory.

**Java compilation failure**

Verify that JDK 25 is installed and selected.

**Claude API request fails**

Check the internet connection, API key, model availability, and account credits.

**Product search returns no results**

The application depends on available online listings and AI-assisted extraction. Product availability and pricing are not guaranteed.

## 10. Technologies

- Java 25
- Maven
- JavaFX
- Anthropic Claude API
- Jackson JSON
- JUnit 5
- Ikonli
