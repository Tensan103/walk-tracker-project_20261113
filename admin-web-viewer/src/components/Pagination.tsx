type Props = {
  currentPage: number;
  totalItems: number;
  pageSize?: number;
  onPageChange: (page: number) => void;
};

export function Pagination({ currentPage, totalItems, pageSize = 20, onPageChange }: Props) {
  if (totalItems === 0) return null;

  const totalPages = Math.ceil(totalItems / pageSize);
  const start = (currentPage - 1) * pageSize + 1;
  const end = Math.min(currentPage * pageSize, totalItems);
  const isFirst = currentPage <= 1;
  const isLast = currentPage >= totalPages;

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '14px', padding: '8px 0' }}>
      <button onClick={() => onPageChange(currentPage - 1)} disabled={isFirst} style={btnStyle(isFirst)}>
        前へ
      </button>
      <span style={{ color: '#555' }}>
        {start}〜{end}件 / 全{totalItems}件（{currentPage} / {totalPages}ページ）
      </span>
      <button onClick={() => onPageChange(currentPage + 1)} disabled={isLast} style={btnStyle(isLast)}>
        次へ
      </button>
    </div>
  );
}

const btnStyle = (disabled: boolean): React.CSSProperties => ({
  padding: '5px 12px',
  backgroundColor: disabled ? '#e0e0e0' : '#1976D2',
  color: disabled ? '#888' : '#fff',
  border: 'none',
  borderRadius: '4px',
  cursor: disabled ? 'default' : 'pointer',
  fontSize: '13px',
});
