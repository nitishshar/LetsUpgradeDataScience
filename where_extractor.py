import re
from typing import List, Dict, Optional

def extract_where_conditions(sql: str) -> List[Dict[str, str]]:
    """
    Extract WHERE clause conditions from SQL queries (including nested subqueries).
    
    Args:
        sql: SQL query string
        
    Returns:
        List of dicts with keys: column_name, table_name, operator, value
    """
    
    def parse_query(query: str, table_aliases: Dict[str, str] = None) -> List[Dict[str, str]]:
        if table_aliases is None:
            table_aliases = {}
        
        conditions = []
        
        # Extract table aliases from FROM and JOIN clauses
        aliases = extract_table_aliases(query)
        table_aliases.update(aliases)
        
        # Find and process subqueries first
        subquery_pattern = r'\(SELECT\s+.*?\sFROM\s+.*?(?:\sWHERE\s+.*?)?\)'
        subqueries = []
        
        # Extract subqueries (handle nested parentheses)
        temp_query = query.upper()
        start_idx = 0
        while True:
            match = re.search(r'\(SELECT\s+', temp_query[start_idx:], re.IGNORECASE)
            if not match:
                break
            
            # Find matching closing parenthesis
            paren_start = start_idx + match.start()
            paren_count = 0
            paren_end = paren_start
            
            for i in range(paren_start, len(query)):
                if query[i] == '(':
                    paren_count += 1
                elif query[i] == ')':
                    paren_count -= 1
                    if paren_count == 0:
                        paren_end = i
                        break
            
            if paren_count == 0:
                subquery = query[paren_start + 1:paren_end]
                subqueries.append(subquery)
                # Recursively process subquery
                conditions.extend(parse_query(subquery, table_aliases.copy()))
            
            start_idx = paren_end + 1
            if start_idx >= len(query):
                break
        
        # Extract WHERE clause from current query level
        where_match = re.search(r'\bWHERE\s+(.*?)(?:\s+GROUP\s+BY|\s+ORDER\s+BY|\s+HAVING|\s+LIMIT|$)', 
                                query, re.IGNORECASE | re.DOTALL)
        
        if where_match:
            where_clause = where_match.group(1)
            
            # Remove subqueries from WHERE clause to avoid duplicate processing
            for sq in subqueries:
                where_clause = where_clause.replace(f'({sq})', ' __SUBQUERY__ ')
            
            # Parse conditions in WHERE clause
            conditions.extend(parse_where_clause(where_clause, table_aliases))
        
        return conditions
    
    def extract_table_aliases(query: str) -> Dict[str, str]:
        """Extract table aliases from FROM and JOIN clauses."""
        aliases = {}
        
        # Pattern for FROM clause: FROM table_name alias or FROM table_name AS alias
        from_pattern = r'\bFROM\s+(\w+)(?:\s+AS)?\s+(\w+)'
        for match in re.finditer(from_pattern, query, re.IGNORECASE):
            table_name = match.group(1)
            alias = match.group(2)
            if alias.upper() not in ['WHERE', 'JOIN', 'INNER', 'LEFT', 'RIGHT', 'OUTER', 'ON']:
                aliases[alias] = table_name
        
        # Pattern for JOIN clauses: JOIN table_name alias or JOIN table_name AS alias
        join_pattern = r'\bJOIN\s+(\w+)(?:\s+AS)?\s+(\w+)'
        for match in re.finditer(join_pattern, query, re.IGNORECASE):
            table_name = match.group(1)
            alias = match.group(2)
            if alias.upper() not in ['ON', 'WHERE', 'AND', 'OR']:
                aliases[alias] = table_name
        
        return aliases
    
    def parse_where_clause(where_clause: str, table_aliases: Dict[str, str]) -> List[Dict[str, str]]:
        """Parse WHERE clause and extract conditions."""
        conditions = []
        
        # Split by AND/OR (simple split, doesn't handle nested parentheses perfectly)
        condition_parts = re.split(r'\s+(?:AND|OR)\s+', where_clause, flags=re.IGNORECASE)
        
        for part in condition_parts:
            part = part.strip()
            if not part or part == '__SUBQUERY__':
                continue
            
            # Extract condition details
            condition = parse_condition(part, table_aliases)
            if condition:
                conditions.append(condition)
        
        return conditions
    
    def strip_functions(column_expr: str) -> str:
        """Remove SQL functions like CAST, NVL, UPPER, etc. and extract column name."""
        # Remove common SQL functions
        functions = ['CAST', 'NVL', 'NVL2', 'COALESCE', 'UPPER', 'LOWER', 'TRIM', 
                     'LTRIM', 'RTRIM', 'TO_DATE', 'TO_CHAR', 'TO_NUMBER', 'SUBSTR']
        
        expr = column_expr.strip()
        
        # Remove function calls
        for func in functions:
            pattern = rf'\b{func}\s*\('
            while re.search(pattern, expr, re.IGNORECASE):
                expr = re.sub(pattern, '', expr, flags=re.IGNORECASE)
        
        # Remove parentheses and AS clauses for CAST
        expr = re.sub(r'\s+AS\s+\w+', '', expr, flags=re.IGNORECASE)
        expr = expr.replace('(', '').replace(')', '')
        
        # Extract column name (table.column or column)
        column_match = re.search(r'(\w+\.)?(\w+)', expr)
        if column_match:
            return column_match.group(0)
        
        return expr.strip()
    
    def parse_condition(condition: str, table_aliases: Dict[str, str]) -> Optional[Dict[str, str]]:
        """Parse a single condition into column, table, operator, value."""
        condition = condition.strip()
        
        # Match various operators
        operators = ['<=', '>=', '<>', '!=', '=', '<', '>', 'LIKE', 'IN', 'NOT IN', 
                     'IS NULL', 'IS NOT NULL', 'BETWEEN']
        
        for op in operators:
            pattern = rf'(.+?)\s+{op}\s+(.+)' if op not in ['IS NULL', 'IS NOT NULL'] else rf'(.+?)\s+{op}'
            match = re.search(pattern, condition, re.IGNORECASE)
            
            if match:
                column_expr = match.group(1).strip()
                column_full = strip_functions(column_expr)
                
                # Extract table and column name
                if '.' in column_full:
                    table_ref, column_name = column_full.split('.', 1)
                    table_name = table_aliases.get(table_ref, table_ref)
                else:
                    table_name = ''
                    column_name = column_full
                
                # Extract value
                value = match.group(2).strip() if len(match.groups()) > 1 else ''
                value = value.strip("'\"")
                
                return {
                    'column_name': column_name,
                    'table_name': table_name,
                    'operator': op.upper(),
                    'value': value
                }
        
        return None
    
    # Start parsing
    return parse_query(sql)


# Example usage
if __name__ == "__main__":
    # Test case 1: Simple query
    sql1 = """
    SELECT * FROM employees e
    WHERE e.salary > 50000 AND e.department_id = 10
    """
    
    # Test case 2: Query with JOIN and table aliases
    sql2 = """
    SELECT e.name, d.dept_name
    FROM employees e
    JOIN departments d ON e.dept_id = d.id
    WHERE e.salary >= 60000 AND d.location = 'NYC'
    """
    
    # Test case 3: Query with subquery
    sql3 = """
    SELECT * FROM employees e
    WHERE e.dept_id IN (
        SELECT d.id FROM departments d
        WHERE d.budget > 100000
    ) AND e.salary > 50000
    """
    
    # Test case 4: Query with SQL functions
    sql4 = """
    SELECT * FROM employees
    WHERE UPPER(name) = 'JOHN' 
    AND CAST(hire_date AS DATE) >= '2020-01-01'
    AND NVL(bonus, 0) > 1000
    """
    
    print("Test 1 - Simple Query:")
    print(extract_where_conditions(sql1))
    print("\nTest 2 - Query with JOIN:")
    print(extract_where_conditions(sql2))
    print("\nTest 3 - Query with Subquery:")
    print(extract_where_conditions(sql3))
    print("\nTest 4 - Query with SQL Functions:")
    print(extract_where_conditions(sql4))
