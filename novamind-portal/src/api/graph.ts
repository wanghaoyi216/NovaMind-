import request from '@/utils/request'

export interface GraphNode {
  id: string
  label: string
  type: string
}

export interface GraphEdge {
  from: string
  to: string
  label: string
}

export interface GraphData {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export interface GraphStatus {
  enabled: boolean
  uri?: string
  database?: string
}

export function getGraphVisualization(): Promise<GraphData> {
  return request.get('/ais/graph/visualization').then((res: any) => res.data || { nodes: [], edges: [] })
}

export function getGraphStatus(): Promise<GraphStatus> {
  return request.get('/ais/graph/status').then((res: any) => res.data || { enabled: false })
}
