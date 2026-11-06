import request from '@/utils/request'
import type {
  PaginationParams,
  StudentSearchCriteria,
  SearchStudentByCriteriaResponse,
  CreateStudentResponse,
  DeactivateStudentResponse,
  DeleteStudentResponse,
  FindStudentByIdResponse,
  UpdateStudentResponse,
  Student,
  RegistrationParams,
  FindStudentByTeamIdResponse
} from './types'

enum API {
  STUDENTS_ENDPOINT = '/students',
  SEARCH_STUDENTS_ENDPOINT = '/students/search'
}

export const searchStudents = (params: PaginationParams, searchCriteria: StudentSearchCriteria) =>
  request.post<any, SearchStudentByCriteriaResponse>(API.SEARCH_STUDENTS_ENDPOINT, searchCriteria, {
    params
  })

export const findStudentById = (id: number) =>
  request.get<any, FindStudentByIdResponse>(`${API.STUDENTS_ENDPOINT}/${id}`)

export const findStudentsByTeamId = (teamId: number) =>
  request.get<any, FindStudentByTeamIdResponse>(`${API.STUDENTS_ENDPOINT}/teams/${teamId}`)

export const createStudent = (newStudent: Student, params: RegistrationParams) =>
  request.post<any, CreateStudentResponse>(API.STUDENTS_ENDPOINT, newStudent, { params })

export const updateStudent = (updatedStudent: Student) =>
  request.put<any, UpdateStudentResponse>(
    `${API.STUDENTS_ENDPOINT}/${updatedStudent.id}`,
    updatedStudent
  )

// The server emails her before it answers, so it gets longer than the default 10 seconds. Otherwise a slow mail server
// would cost the admin the alert for a deactivation that has already committed (UC-STU-deactivate-student 6b).
export const deactivateStudent = (id: number) =>
  request.post<any, DeactivateStudentResponse>(
    `${API.STUDENTS_ENDPOINT}/${id}/deactivate`,
    undefined,
    { timeout: 120_000 }
  )

export const deleteStudent = (id: number) =>
  request.delete<any, DeleteStudentResponse>(`${API.STUDENTS_ENDPOINT}/${id}`)
