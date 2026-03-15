import React from 'react';
import styles from './DiffViewer.module.css';

export default function DiffViewer({ children }) {
  const lines = children.split('\n');

  return (
    <pre className={styles.diffContainer}>
      {lines.map((line, i) => {
        const diffMatch = line.match(/^(\s?[+-])(.*)/);

        if (diffMatch) {
            const symbolPart = diffMatch[1];
            if (symbolPart.trim() === '+') {
                return (
                    <div key={i} className={styles.added}>
                        {line}
                    </div>
                );
            } else if (symbolPart.trim() === '-') {
                return (
                    <div key={i} className={styles.removed}>
                        {line}
                    </div>
                );
            }
        }
        
        return <div key={i}>{line}</div>;
      })}
    </pre>
  );
}